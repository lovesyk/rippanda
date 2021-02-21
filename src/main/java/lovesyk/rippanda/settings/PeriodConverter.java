package lovesyk.rippanda.settings;

import java.time.Duration;

import picocli.CommandLine.ITypeConverter;

/**
 * The converter to process user-input period durations.
 */
class PeriodConverter implements ITypeConverter<Duration> {
    private static final char PERIOD_PREFIX = 'P';

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration convert(String value) throws Exception {
        return Duration.parse(PERIOD_PREFIX + value);
    }
}
