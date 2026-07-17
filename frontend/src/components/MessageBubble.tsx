import type { ChatMessage } from "../hooks/useChat";
import { FilterChips } from "./FilterChips";
import { MetaPanel } from "./MetaPanel";
import { RoomCardList } from "./RoomCardList";

export function MessageBubble({
  message,
  onRemoveFilter,
}: {
  message: ChatMessage;
  onRemoveFilter: (sentence: string) => void;
}) {
  const roleClass =
    message.role === "user" ? "bubble--user" : message.role === "error" ? "bubble--error" : "bubble--bot";

  return (
    <div className={`bubble ${roleClass}`}>
      <div className="bubble__text">{message.text}</div>
      {message.rooms && message.rooms.length > 0 && <RoomCardList rooms={message.rooms} />}
      {message.activeFilters && (
        <FilterChips filters={message.activeFilters} onRemoveFilter={onRemoveFilter} />
      )}
      {message.meta && <MetaPanel meta={message.meta} />}
    </div>
  );
}
