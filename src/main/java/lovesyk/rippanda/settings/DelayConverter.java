package lovesyk.rippanda.settings;

import java.time.Duration;

import picocli.CommandLine.ITypeConverter;

/**
 * The converter to process user-input delays.
 */
class DelayConverter implements ITypeConverter<Duration> {
    private static final String TIME_PREFIX = "PT";

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration convert(String value) throws Exception {
        return Duration.parse(TIME_PREFIX + value);
    }
}
