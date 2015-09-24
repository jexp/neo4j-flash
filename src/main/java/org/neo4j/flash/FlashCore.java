package org.neo4j.flash;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import org.neo4j.driver.*;
import org.neo4j.driver.internal.spi.Logger;
import org.neo4j.driver.internal.spi.Logging;
import rx.Observable;
import rx.subjects.AsyncSubject;
import rx.subjects.Subject;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author mh
 * @since 24.09.15
 */
public class FlashCore {

    private final Config config;

    static class Log {
        private Logger log;

        public Log(Logging logging, Class<FlashCore> type) {
            this.log = logging.getLog(type.getName());
        }

        public void warn(String message, Object value, Throwable ex) {
            // todo string.format
            log.error(message,ex);
        }

        public void info(String message, Throwable ex) {
            log.info(message,ex);
        }

        public void warn(String message, Throwable ex) {
            log.error(message,ex);
        }
    }

    private final Log LOGGER;


    public static class BoltResponse {
        Result result;

        BoltResponse(Result result) {
            this.result = result;
        }
    }

    public static class BoltRequest {
        public final String query;
        public final Map<String, Value> params;
        public final Subject<BoltResponse, BoltResponse> observable;
        public String url;

        BoltRequest(String query, Map<String, Value> params) {
            this.query = query;
            this.params = params;
            this.observable = AsyncSubject.create();
        }
    }

    static class RequestEvent {
        BoltRequest request;

        public void setRequest(BoltRequest request) {
            this.request = request;
        }
    }

    static class ResponseEvent {
        public Subject<BoltResponse, BoltResponse> observable;
        public Result result;
    }
    
    static class RequestEventFactory implements EventFactory<RequestEvent> {
        @Override
        public RequestEvent newInstance() {
            return new RequestEvent();
        }
    }
    static class ResponseEventFactory implements EventFactory<ResponseEvent> {
        @Override
        public ResponseEvent newInstance() {
            return new ResponseEvent();
        }
    }
    
    static class FlashEnvironment {

        public int responseBufferSize() {
            return 2^14; // 16k
        }

        public int requestBufferSize() {
            return 2^14; // 16k
        }
    }
    
        /**
         * Translates {@link BoltRequest}s into {@link RequestEvent}s.
         */
        private static final EventTranslatorOneArg<RequestEvent, BoltRequest> REQUEST_TRANSLATOR =
                new EventTranslatorOneArg<RequestEvent, BoltRequest>() {
                    @Override
                    public void translateTo(RequestEvent event, long sequence, BoltRequest request) {
                        event.setRequest(request);
                    }
                };

        /**
         * A preconstructed {@link BackpressureException}.
         */
        private static final BackpressureException BACKPRESSURE_EXCEPTION = new BackpressureException();

        /**
         * The {@link RequestEvent} {@link RingBuffer}.
         */
        private final RingBuffer<RequestEvent> requestRingBuffer;

        /**
         * The handler for all cluster nodes.
         */
        private final EventHandler<RequestEvent> requestHandler;

        private final FlashEnvironment environment;
        private final Disruptor<RequestEvent> requestDisruptor;
        private final Disruptor<ResponseEvent> responseDisruptor;
        private final ExecutorService disruptorExecutor;

        /**
         * Populate the static exceptions with stack trace elements.
         */
        static {
            BACKPRESSURE_EXCEPTION.setStackTrace(new StackTraceElement[0]);
        }

        @SuppressWarnings("unchecked")
        public FlashCore(final FlashEnvironment environment, Config config) {
            this.config = config;
            LOGGER = new Log(config.logging(),FlashCore.class);

            this.environment = environment;
            disruptorExecutor = Executors.newFixedThreadPool(2); // , new DefaultThreadFactory("cb-core", true));

            responseDisruptor = new Disruptor<>(
                    new ResponseEventFactory(),
                    environment.responseBufferSize(),
                    disruptorExecutor
            );
            responseDisruptor.handleExceptionsWith(new ExceptionHandler() {
                @Override
                public void handleEventException(Throwable ex, long sequence, Object event) {
                    LOGGER.warn("Exception while Handling Response Events {}, {}", event, ex);
                }

                @Override
                public void handleOnStartException(Throwable ex) {
                    LOGGER.warn("Exception while Starting Response RingBuffer {}", ex);
                }

                @Override
                public void handleOnShutdownException(Throwable ex) {
                    LOGGER.info("Exception while shutting down Response RingBuffer {}", ex);
                }
            });
            responseDisruptor.handleEventsWith(new ResponseHandler(environment, this));
            responseDisruptor.start();
            RingBuffer<ResponseEvent> responseRingBuffer = responseDisruptor.getRingBuffer();

            requestDisruptor = new Disruptor<>(
                    new RequestEventFactory(),
                    environment.requestBufferSize(),
                    disruptorExecutor
            );
            requestHandler = new RequestHandler(environment, responseRingBuffer);
            requestDisruptor.handleExceptionsWith(new ExceptionHandler() {
                @Override
                public void handleEventException(Throwable ex, long sequence, Object event) {
                    LOGGER.warn("Exception while Handling Request Events {}, {}", event, ex);
                }

                @Override
                public void handleOnStartException(Throwable ex) {
                    LOGGER.warn("Exception while Starting Request RingBuffer {}", ex);
                }

                @Override
                public void handleOnShutdownException(Throwable ex) {
                    LOGGER.info("Exception while shutting down Request RingBuffer {}", ex);
                }
            });
            requestDisruptor.handleEventsWith(requestHandler);
            requestDisruptor.start();
            requestRingBuffer = requestDisruptor.getRingBuffer();
        }

        @SuppressWarnings("unchecked")
        public <R extends BoltResponse> Observable<R> send(BoltRequest request) {
            boolean published = requestRingBuffer.tryPublishEvent(REQUEST_TRANSLATOR, request);
            if (!published) {
                request.observable.onError(BACKPRESSURE_EXCEPTION);
            }
            return (Observable<R>) request.observable;
        }


    private static class BackpressureException extends RuntimeException {
    }

    private static class RequestHandler implements EventHandler<RequestEvent> {
        private final FlashEnvironment environment;
        private final RingBuffer<ResponseEvent> responseRingBuffer;

        public RequestHandler(FlashEnvironment environment, RingBuffer<ResponseEvent> responseRingBuffer) {
            this.environment = environment;
            this.responseRingBuffer = responseRingBuffer;
        }

        @Override
        public void onEvent(final RequestEvent event, long sequence, boolean endOfBatch) throws Exception {
            BoltRequest request = event.request;
            final Result resultHandle = GraphDatabase.driver("bolt://localhost").session().run(request.query, request.params); // blocking
            // would be nice if it is was streaming then we could put one record on the responseDisruptor

            responseRingBuffer.publishEvent((responseEvent, sequence1) -> {
                responseEvent.result = resultHandle;
                responseEvent.observable = request.observable;
            });
        }
    }

    private class ResponseHandler implements EventHandler<ResponseEvent> {
        public ResponseHandler(FlashEnvironment environment, FlashCore flashCore) {
        }

        @Override
        public void onEvent(ResponseEvent event, long sequence, boolean endOfBatch) throws Exception {
            Subject<BoltResponse, BoltResponse> observable = event.observable;
            try {
                observable.onNext(new BoltResponse(event.result));
            } catch(Exception e) {
                observable.onError(e);
            } finally {
                observable.onCompleted();
            }
        }
    }
}
