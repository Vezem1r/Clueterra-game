SELECT country.iso_alpha2, statistic.source, statistic.indicator_code, statistic.value,
       statistic.value_text, statistic.year
FROM cl_schema.country_statistics statistic
JOIN cl_schema.countries country ON country.id = statistic.country_id
WHERE statistic.is_current
  AND country.iso_alpha2 IN (:codes)
  AND statistic.indicator_code IN ('SP.POP.TOTL', 'GEO_AREA', 'GEO_CAPITAL', 'CAPITAL_CITY',
                                   'HIGHEST_POINT', 'COASTLINE', 'WIKIDATA_DRIVING_SIDE',
                                   'GEO_CURRENCY_CODE', 'CURRENCY', 'OWID_LIFE_EXPECTANCY', 'SP.DYN.LE00.IN',
                                   'NY.GDP.PCAP.CD', 'OWID_TOURIST_ARRIVALS', 'FOREST_LAND_PCT', 'MEDIAN_AGE',
                                   'WORLD_HERITAGE_SITES_COUNT', 'WIKIDATA_TOP_LEVEL_DOMAIN', 'BORDER_COUNTRIES')
ORDER BY country.iso_alpha2, statistic.indicator_code, statistic.source
