package com.simulation.demo.entity.converter;

import com.simulation.demo.entity.SimulationStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Deprecated
public class SimulationStatusConverter implements AttributeConverter<Object, String> {
    // 已废弃，统一用Experiment.State
    @Override
    public String convertToDatabaseColumn(Object attribute) { return null; }
    @Override
    public Object convertToEntityAttribute(String dbData) { return null; }
}
