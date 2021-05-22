package lovesyk.rippanda.settings;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * The converter to process user-input update intervals.
 */
class UpdateIntervalConverter implements ITypeConverter<UpdateInterval> {
    private static final PeriodConverter PERIOD_CONVERTER = new PeriodConverter();
    private static final Pattern PATTERN = Pattern.compile("^(.*?)=(.*?)\\-(.*?)=(.*?)$");

    /**
     * {@inheritDoc}
     */
    @Override
    public UpdateInterval convert(String value) throws Exception {
        Matcher matcher = PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new TypeConversionException("Invalid format: must be 'minThreshold=minDuration-maxThreshold=maxDuration' but was '" + value + "'");
        }

        Duration minThreshold = PERIOD_CONVERTER.convert(matcher.group(1));
        Duration minDuration = PERIOD_CONVERTER.convert(matcher.group(2));
        Duration maxThreshold = PERIOD_CONVERTER.convert(matcher.group(3));
        Duration maxDuration = PERIOD_CONVERTER.convert(matcher.group(4));

        return new UpdateInterval(minThreshold, minDuration, maxThreshold, maxDuration);
    }
}
