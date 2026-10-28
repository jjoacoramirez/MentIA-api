package com.mentia.profile.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AgeRangeConverter implements AttributeConverter<AgeRange, String> {

    @Override
    public String convertToDatabaseColumn(AgeRange attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public AgeRange convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AgeRange.fromValue(dbData);
    }
}
