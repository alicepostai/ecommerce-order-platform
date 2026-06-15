package com.ecommerce.orders.testutil;

import org.testcontainers.dockerclient.DockerClientProviderStrategy;
import org.testcontainers.dockerclient.TransportConfig;

import java.net.URI;

/**
 * Conecta ao Docker Desktop 4.40+ no Windows via named pipe correto.
 * Docker Desktop 4.40+ usa dockerDesktopLinuxEngine para containers Linux;
 * as estratégias built-in do Testcontainers tentam docker_engine (retorna 400).
 */
public class DockerDesktopWindowsStrategy extends DockerClientProviderStrategy {

    @Override
    public String getDescription() {
        return "Docker Desktop Windows 4.40+ (dockerDesktopLinuxEngine named pipe)";
    }

    @Override
    public boolean isApplicable() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    @Override
    protected int getPriority() {
        return 900;
    }

    @Override
    public TransportConfig getTransportConfig() {
        return TransportConfig.builder()
                .dockerHost(URI.create("tcp://localhost:2375"))
                .build();
    }
}
