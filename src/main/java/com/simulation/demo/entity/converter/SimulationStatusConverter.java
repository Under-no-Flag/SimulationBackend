package com.simulation.demo.entity.converter;

import com.simulation.demo.entity.SimulationStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SimulationStatusConverter implements AttributeConverter<SimulationStatus, String> {

    @Override
    public String convertToDatabaseColumn(SimulationStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.toDatabaseValue();
    }

    @Override
    public SimulationStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return SimulationStatus.fromDatabaseValue(dbData);
    }
}
