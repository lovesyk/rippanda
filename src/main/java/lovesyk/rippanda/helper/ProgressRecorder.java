package lovesyk.rippanda.helper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class ProgressRecorder {
    private final static int MAX_RECORDED_MILESTONES = 1000;

    private final List<LocalDateTime> recordedMilestones = new ArrayList<>();
    private int milestonesReached = 0;

    public void saveMilestone() {
        ++milestonesReached;
        recordedMilestones.add(LocalDateTime.now());

        while (recordedMilestones.size() > MAX_RECORDED_MILESTONES) {
            recordedMilestones.remove(0);
        }
    }

    public String toProgressString(int maxMilestones) {
        Double percentage = calculatePercentage(maxMilestones);
        Duration eta = calculateEta(maxMilestones);
        if (percentage == null || eta == null) {
            return StringUtils.EMPTY;
        }

        // https://stackoverflow.com/a/40487511
        String humanReadableEta = eta.toString()
                .substring(2)
                .replaceAll("(\\d[HMS])(?!$)", "$1 ")
                .toLowerCase();

        return String.format(" (%.2f%% ETA: %s)", percentage, humanReadableEta);
    }

    private Double calculatePercentage(int maxMilestones) {
        int upperLimit = Math.max(milestonesReached, maxMilestones);
        if (upperLimit == 0) {
            return null;
        }

        return 100d * milestonesReached / upperLimit;
    }

    private Duration calculateEta(int maxMilestones) {
        if (milestonesReached < 1 || recordedMilestones.isEmpty()) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        Duration recordedDuration = Duration.between(recordedMilestones.get(0), now);
        Duration averageDuration = recordedDuration.dividedBy(recordedMilestones.size());
        int remainingMilestones = Math.max(maxMilestones - milestonesReached, 0);
        return averageDuration.multipliedBy(remainingMilestones).truncatedTo(ChronoUnit.SECONDS);
    }
}