import type { Filters } from "../types/chat";
import { buildSearchSentence, buildSearchSentenceWithoutUtility } from "../utils/filtersToText";

interface Chip {
  key: string;
  label: string;
  onRemove: () => void;
}

export function FilterChips({
  filters,
  onRemoveFilter,
}: {
  filters: Filters;
  onRemoveFilter: (sentence: string) => void;
}) {
  const chips: Chip[] = [];

  if (filters.price_max != null) {
    chips.push({
      key: "price_max",
      label: `Dưới ${(filters.price_max / 1_000_000).toString()} triệu`,
      onRemove: () => onRemoveFilter(buildSearchSentence(filters, "price_max")),
    });
  }
  if (filters.price_min != null) {
    chips.push({
      key: "price_min",
      label: `Từ ${(filters.price_min / 1_000_000).toString()} triệu`,
      onRemove: () => onRemoveFilter(buildSearchSentence(filters, "price_min")),
    });
  }
  if (filters.location) {
    chips.push({
      key: "location",
      label: filters.location,
      onRemove: () => onRemoveFilter(buildSearchSentence(filters, "location")),
    });
  }
  if (filters.poi) {
    chips.push({
      key: "poi",
      label: `Gần ${filters.poi}`,
      onRemove: () => onRemoveFilter(buildSearchSentence(filters, "poi")),
    });
  }
  if (filters.area_min != null) {
    chips.push({
      key: "area_min",
      label: `Trên ${filters.area_min}m²`,
      onRemove: () => onRemoveFilter(buildSearchSentence(filters, "area_min")),
    });
  }
  for (const u of filters.utilities) {
    chips.push({
      key: `utility:${u}`,
      label: u,
      onRemove: () => onRemoveFilter(buildSearchSentenceWithoutUtility(filters, u)),
    });
  }

  if (chips.length === 0) return null;

  return (
    <div className="filter-chips">
      {chips.map((c) => (
        <span className="filter-chip" key={c.key}>
          {c.label}
          <button
            type="button"
            className="filter-chip__remove"
            aria-label={`Bỏ điều kiện ${c.label}`}
            onClick={c.onRemove}
          >
            ×
          </button>
        </span>
      ))}
    </div>
  );
}
