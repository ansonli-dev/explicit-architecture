package com.example.seedwork.infrastructure.bus;

import com.example.seedwork.application.command.CommandHandler;
import com.example.seedwork.application.query.QueryHandler;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
public class BusAutoConfiguration {

    @Bean
    SpringCommandBus springCommandBus(List<CommandHandler<?, ?>> handlerList, MeterRegistry meterRegistry) {
        return new SpringCommandBus(handlerList, meterRegistry);
    }

    @Bean
    SpringQueryBus springQueryBus(List<QueryHandler<?, ?>> handlerList, MeterRegistry meterRegistry) {
        return new SpringQueryBus(handlerList, meterRegistry);
    }
}
