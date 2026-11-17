package com.example.catalog.interfaces.rest.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("demo.v2")
public record DemoCatalogProperties(@DefaultValue("true") boolean adminWritesEnabled) {}
