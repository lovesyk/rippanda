package lovesyk.rippanda.settings;

import java.time.Duration;

import picocli.CommandLine.ITypeConverter;

/**
 * The converter to process user-input update intervals.
 */
class UpdateIntervalConverter implements ITypeConverter<UpdateInterval> {
    private static final PeriodConverter PERIOD_CONVERTER = new PeriodConverter();

    /**
     * {@inheritDoc}
     */
    @Override
    public UpdateInterval convert(String value) throws Exception {
        String[] valueSplit = value.split("-", 2);
        Duration minDuration = PERIOD_CONVERTER.convert(valueSplit[0]);
        Duration maxDuration = valueSplit.length > 1 ? PERIOD_CONVERTER.convert(valueSplit[1]) : minDuration;

        return new UpdateInterval(minDuration, maxDuration);
    }
}
