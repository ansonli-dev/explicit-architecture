package com.example.catalog.interfaces.rest.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("demo.catalog")
public record DemoCatalogProperties(@DefaultValue("true") boolean adminWritesEnabled) {}
