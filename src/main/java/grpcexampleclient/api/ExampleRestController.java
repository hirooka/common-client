package grpcexampleclient.api;

import io.grpc.ManagedChannelBuilder;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("api")
@RestController
public class ExampleRestController {

    @Value("${grpc.server.host}")
    private String grpcHost;

    @Value("${grpc.server.port}")
    private int grpcPort;

    @Value("${grpc.server.secure}")
    private boolean isGrpcSecure;

    @Value("${rest.server.host}")
    private String restHost;

    @Value("${rest.server.port}")
    private int restPort;

    @Value("${rest.server.secure}")
    private boolean isRestSecure;

    private final WebClient webClient;

    @Value("${app.env}")
    private String envString;

    private GreeterGrpc.GreeterBlockingStub blockingStub() {
        if (isGrpcSecure) {
            return GreeterGrpc.newBlockingStub(ManagedChannelBuilder.forAddress(grpcHost, grpcPort).useTransportSecurity().build());
        } else {
            return GreeterGrpc.newBlockingStub(ManagedChannelBuilder.forAddress(grpcHost, grpcPort).usePlaintext().build());
        }
    }

    private GreeterGrpc.GreeterStub asyncStub() {
        if (isGrpcSecure) {
            return GreeterGrpc.newStub(ManagedChannelBuilder.forAddress(grpcHost, grpcPort).useTransportSecurity().build());
        } else {
            return GreeterGrpc.newStub(ManagedChannelBuilder.forAddress(grpcHost, grpcPort).usePlaintext().build());
        }
    }

    @GetMapping("u")
    String unary() {
        log.info("gRPC: Unary");
        final HelloRequest request = HelloRequest.newBuilder().setName("Tom").build();
        return blockingStub().sayHelloUnary(request).getMessage();
    }

    @GetMapping("ss")
    String serverStreaming() {
        log.info("gRPC: Server streaming");
        final HelloRequest request = HelloRequest.newBuilder().setName("Tom").build();
        final Iterator<HelloReply> replies = blockingStub().sayHelloServerStreaming(request);
        final List<HelloReply> response = new ArrayList<>();
        while (replies.hasNext()) {
            response.add(replies.next());
        }
        return response.toString();
    }

    @GetMapping("cs")
    String clientStreaming() throws InterruptedException {
        log.info("gRPC: Client streaming");
        final HelloRequest request = HelloRequest.newBuilder().setName("Tom").build();
        final CountDownLatch finishLatch = new CountDownLatch(1);
        final List<HelloReply> response = new ArrayList<>();
        final StreamObserver<HelloRequest> streamObserver = asyncStub().sayHelloClientStreaming(new StreamObserver<HelloReply>() {
            @Override
            public void onNext(HelloReply reply) {
                response.add(reply);
            }
            @Override
            public void onError(Throwable t) {
                // ...
            }
            @Override
            public void onCompleted() {
                finishLatch.countDown();
            }
        });
        streamObserver.onNext(request);
        streamObserver.onNext(request);
        streamObserver.onNext(request);
        streamObserver.onCompleted();
        finishLatch.await(10, TimeUnit.SECONDS);
        return response.toString();
    }

    @GetMapping("bi")
    String bidirectionalStreaming() throws InterruptedException {
        log.info("gRPC: Bidirectional streaming");
        final HelloRequest request = HelloRequest.newBuilder().setName("Tom").build();
        final CountDownLatch finishLatch = new CountDownLatch(1);
        final List<HelloReply> response = new ArrayList<>();
        final StreamObserver<HelloRequest> streamObserver = asyncStub().sayHelloBidirectionalStreaming(new StreamObserver<HelloReply>() {
            @Override
            public void onNext(HelloReply reply) {
                response.add(reply);
            }
            @Override
            public void onError(Throwable t) {
                // ...
            }
            @Override
            public void onCompleted() {
                finishLatch.countDown();
            }
        });
        streamObserver.onNext(request);
        streamObserver.onNext(request);
        streamObserver.onNext(request);
        streamObserver.onCompleted();
        finishLatch.await(10, TimeUnit.SECONDS);
        return response.toString();
    }

    @GetMapping("r")
    Flux<Map<String, Object>> rest() {
        log.info("REST: ");
        final String scheme;
        if (isRestSecure) {
            scheme = "https";
        } else {
            scheme = "http";
        }
        final Flux<Map<String, Object>> dateFlux = WebClient.builder().baseUrl(scheme + "://" + restHost + ":" + restPort).build()
                .get()
                .uri("/api/test")
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<Map<String, Object>>() {});
        return dateFlux;
    }

    @GetMapping("ro")
    Flux<Map<String, Object>> restOauth2() {
        log.info("REST(OAuth 2.0): ");
        final String scheme;
        if (isRestSecure) {
            scheme = "https";
        } else {
            scheme = "http";
        }
        return webClient
                .get()
                .uri(scheme + "://" + restHost + ":" + restPort + "/api/test")
                .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId("custom"))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @GetMapping("e")
    String env() {
        log.info("ENV_STRING: {}", envString);
        return envString;
    }
}
