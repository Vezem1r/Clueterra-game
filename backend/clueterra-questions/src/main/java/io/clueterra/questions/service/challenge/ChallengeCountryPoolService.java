package io.clueterra.questions.service.challenge;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import io.clueterra.questions.config.ChallengeProfile;
import io.clueterra.questions.config.CountryVisibilityProfile;
import io.clueterra.questions.config.RegionQuota;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.enums.RegionGroup;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChallengeCountryPoolService {

    private final CountryVisibilityProfileService profileService;

    public List<Country> selectCountries(
            LocalDate date,
            ChallengeProfile profile,
            int targetCount,
            Set<Long> excludeIds,
            List<Country> allCountries) {

        List<EnrichedCountry> candidates =
                allCountries.stream()
                        .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                        .filter(c -> !excludeIds.contains(c.getId()))
                        .map(
                                c -> {
                                    CountryVisibilityProfile vp = profileService.getProfile(c.getIsoAlpha2());
                                    return new EnrichedCountry(c, vp);
                                })
                        .filter(ec -> profile.allowedCountryTiers().contains(ec.profile().visibilityTier()))
                        .filter(ec -> !profile.easyEligibleOnly() || ec.profile().easyEligible())
                        .toList();

        if (candidates.size() <= targetCount) {
            return candidates.stream()
                    .sorted(Comparator.comparingDouble(ec -> ec.profile().score()))
                    .map(EnrichedCountry::country)
                    .toList();
        }

        Map<RegionGroup, List<EnrichedCountry>> byRegion =
                candidates.stream()
                        .collect(
                                Collectors.groupingBy(
                                        ec -> ec.profile().regionGroup(),
                                        () -> new EnumMap<>(RegionGroup.class),
                                        Collectors.toList()));

        Random random = new Random(date.toEpochDay() * 31L + profile.level().ordinal());
        List<EnrichedCountry> selected = new ArrayList<>();

        Map<RegionGroup, Integer> regionTargets =
                computeRegionTargets(profile, targetCount, byRegion);

        for (Map.Entry<RegionGroup, Integer> entry : regionTargets.entrySet()) {
            RegionGroup region = entry.getKey();
            int regionSlots = entry.getValue();
            List<EnrichedCountry> regionCandidates =
                    new ArrayList<>(byRegion.getOrDefault(region, List.of()));

            selected.addAll(weightedRandomPick(regionCandidates, regionSlots, random));
        }

        if (selected.size() < targetCount) {
            Set<Long> selectedIds =
                    selected.stream().map(ec -> ec.country().getId()).collect(Collectors.toSet());
            List<EnrichedCountry> remaining =
                    new ArrayList<>(
                            candidates.stream()
                                    .filter(ec -> !selectedIds.contains(ec.country().getId()))
                                    .toList());

            int needed = targetCount - selected.size();
            selected.addAll(weightedRandomPick(remaining, needed, random));
        }

        selected.sort(Comparator.comparingDouble(ec -> ec.profile().score()));

        List<Country> result = selected.stream().map(EnrichedCountry::country).toList();
        if (result.size() > targetCount) {
            result = result.subList(0, targetCount);
        }
        return result;
    }

    private List<EnrichedCountry> weightedRandomPick(
            List<EnrichedCountry> candidates, int count, Random random) {

        List<EnrichedCountry> pool = new ArrayList<>(candidates);
        List<EnrichedCountry> result = new ArrayList<>();

        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            double totalWeight = pool.stream()
                    .mapToDouble(this::countryWeight)
                    .sum();

            if (totalWeight <= 0) {
                result.add(pool.remove(random.nextInt(pool.size())));
                continue;
            }

            double roll = random.nextDouble() * totalWeight;
            double cumulative = 0;
            int selectedIndex = pool.size() - 1;

            for (int j = 0; j < pool.size(); j++) {
                cumulative += countryWeight(pool.get(j));
                if (cumulative >= roll) {
                    selectedIndex = j;
                    break;
                }
            }

            result.add(pool.remove(selectedIndex));
        }

        return result;
    }

    private double countryWeight(EnrichedCountry country) {
        int timesFeatured = country.country().getTimesFeatured() != null
                ? country.country().getTimesFeatured()
                : 0;
        double freshnessWeight = 1.0 / (1.0 + timesFeatured);
        double familiarityWeight = 1.0 / (0.35 + Math.max(0.0, country.profile().score()));
        return freshnessWeight * familiarityWeight;
    }

    private Map<RegionGroup, Integer> computeRegionTargets(
            ChallengeProfile profile,
            int targetCount,
            Map<RegionGroup, List<EnrichedCountry>> byRegion) {

        Map<RegionGroup, RegionQuota> quotas = profile.regionQuotas();
        Map<RegionGroup, Integer> targets = new EnumMap<>(RegionGroup.class);
        int assigned = 0;

        for (Map.Entry<RegionGroup, RegionQuota> entry : quotas.entrySet()) {
            if (assigned >= targetCount) break;
            RegionGroup region = entry.getKey();
            RegionQuota quota = entry.getValue();
            int available = byRegion.getOrDefault(region, List.of()).size();
            int target = Math.min(quota.minCountries(), Math.min(available, targetCount - assigned));
            targets.put(region, target);
            assigned += target;
        }

        if (assigned < targetCount) {
            List<Map.Entry<RegionGroup, RegionQuota>> expandable =
                    quotas.entrySet().stream()
                            .filter(
                                    e -> {
                                        int current = targets.getOrDefault(e.getKey(), 0);
                                        int available = byRegion.getOrDefault(e.getKey(), List.of()).size();
                                        return current < e.getValue().maxCountries() && current < available;
                                    })
                            .sorted(
                                    Comparator.<Map.Entry<RegionGroup, RegionQuota>>comparingInt(
                                            e -> byRegion.getOrDefault(e.getKey(), List.of()).size())
                                            .reversed())
                            .toList();

            for (Map.Entry<RegionGroup, RegionQuota> entry : expandable) {
                if (assigned >= targetCount) break;
                RegionGroup region = entry.getKey();
                int current = targets.getOrDefault(region, 0);
                int max = Math.min(entry.getValue().maxCountries(),
                        byRegion.getOrDefault(region, List.of()).size());
                int add = Math.min(max - current, targetCount - assigned);
                targets.put(region, current + add);
                assigned += add;
            }
        }

        return targets;
    }

}
