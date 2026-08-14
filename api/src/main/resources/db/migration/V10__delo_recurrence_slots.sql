-- Per-weekday recurrence windows as JSON slots.
-- Legacy weekdays + single window stay for backward compatibility.

ALTER TABLE delo
    ADD COLUMN IF NOT EXISTS recurrence_slots TEXT;

UPDATE delo
SET recurrence_slots = (
    SELECT json_agg(json_build_object(
        'weekday', trim(both from d),
        'windowStart', to_char(recurrence_window_start, 'HH24:MI:SS'),
        'windowEnd', to_char(recurrence_window_end, 'HH24:MI:SS')
    ))
    FROM unnest(string_to_array(recurrence_weekdays, ',')) AS d
)
WHERE recurrence_slots IS NULL
  AND recurrence_weekdays IS NOT NULL
  AND recurrence_weekdays <> ''
  AND recurrence_window_start IS NOT NULL
  AND recurrence_window_end IS NOT NULL;
