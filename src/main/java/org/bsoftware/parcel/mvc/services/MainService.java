package org.bsoftware.parcel.mvc.services;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import lombok.RequiredArgsConstructor;
import org.bsoftware.parcel.domain.callbacks.DataProcessingCallback;
import org.bsoftware.parcel.domain.components.DataContainer;
import org.bsoftware.parcel.domain.components.LogView;
import org.bsoftware.parcel.domain.model.DataType;
import org.bsoftware.parcel.domain.model.Proxy;
import org.bsoftware.parcel.domain.model.Source;
import org.bsoftware.parcel.domain.runnables.DataProcessingRunnable;

import java.io.File;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MainService class is used for UI manipulation and thread creation
 *
 * @author Rudolf Barbu
 * @version 1.0.0
 */
@RequiredArgsConstructor
@SuppressWarnings(value = "DanglingJavadoc")
public class MainService implements DataProcessingCallback
{
    /**
     * Setting up the EXECUTORS_SERVICE with demon threads, using thread factory
     */
    static
    {
        EXECUTORS_SERVICE = Executors.newFixedThreadPool(20, runnable ->
        {
            final Thread thread = Executors.defaultThreadFactory().newThread(runnable);

            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);

            return thread;
        });
    }

    /**
     * Realization of thread pool
     */
    private static final ExecutorService EXECUTORS_SERVICE;

    /**
     * Container for completable futures
     */
    private static final EnumMap<DataType, CompletableFuture<Void>> DATA_PROCESSING_COMPLETABLE_FUTURE_MAP = new EnumMap<>(DataType.class);

    /**
     * Sources counter
     */
    private final Label labelSources;

    /**
     * Proxies counter
     */
    private final Label labelProxies;

    /**
     * Custom log container
     */
    private final LogView logViewLog;

    /**
     * Processes data, using asynchronous mechanisms
     *
     * @param optionalFile - file to process
     * @param dataType - data type, on which validation depends
     * @param affectedButton - button which will be disabled, during data processing
     */
    @SuppressWarnings(value = "OptionalUsedAsFieldOrParameterType")
    public void processData(final Optional<File> optionalFile, final DataType dataType, final Button affectedButton)
    {
        if (optionalFile.isPresent())
        {
            final CompletableFuture<Void> completableFuture = DATA_PROCESSING_COMPLETABLE_FUTURE_MAP.get(dataType);

            if ((completableFuture == null) || completableFuture.isDone())
            {
                final DataProcessingRunnable dataProcessingRunnable = new DataProcessingRunnable(optionalFile.get(), dataType, this);

                affectedButton.setDisable(true);
                DATA_PROCESSING_COMPLETABLE_FUTURE_MAP.put(dataType, CompletableFuture.runAsync(dataProcessingRunnable, EXECUTORS_SERVICE).whenComplete((action, throwable) ->
                {
                    affectedButton.setDisable(false);

                    if (throwable != null)
                    {
                        Platform.runLater(() -> logViewLog.error(throwable.getCause().getMessage()));
                    }
                }));
            }
            else
            {
                logViewLog.warning("Cannot run two same tasks in parallel");
            }
        }
        else
        {
            logViewLog.warning("Operation cancelled by user");
        }
    }

    /**
     * Saving processed data and updating counters
     *
     * @param processedData - set with processed data
     * @param dataType - data type, which presented in processed data set
     * @param elapsedTimeInMilliseconds - execution time
     */
    @Override
    @SuppressWarnings(value = "unchecked")
    public void handleProcessedData(final HashSet<?> processedData, final DataType dataType, final long elapsedTimeInMilliseconds)
    {
        if (processedData.isEmpty())
        {
            Platform.runLater(() -> logViewLog.warning(String.format("File with %s returned empty set", dataType.getDataTypeNameInPlural())));
            return;
        }

        if (dataType == DataType.SOURCE)
        {
            DataContainer.refreshSources((HashSet<Source>) processedData);
            Platform.runLater(() -> labelSources.setText(String.valueOf(processedData.size())));
        }
        else if (dataType == DataType.PROXY)
        {
            DataContainer.refreshProxies((HashSet<Proxy>) processedData);
            Platform.runLater(() -> labelProxies.setText(String.valueOf(processedData.size())));
        }

        Platform.runLater(() -> logViewLog.fine(String.format("File with %s processed in %d ms", dataType.getDataTypeNameInPlural(), elapsedTimeInMilliseconds)));
    }
}