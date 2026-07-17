import type { RoomCard as RoomCardType } from "../types/chat";
import { RoomCard } from "./RoomCard";

export function RoomCardList({ rooms }: { rooms: RoomCardType[] }) {
  if (rooms.length === 0) return null;
  return (
    <div className="room-card-list">
      {rooms.map((r) => (
        <RoomCard key={r.id} room={r} />
      ))}
    </div>
  );
}
