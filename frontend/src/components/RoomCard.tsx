import type { RoomCard as RoomCardType } from "../types/chat";

function formatVnd(v: number): string {
  return `${v.toLocaleString("vi-VN")}đ`;
}

export function RoomCard({ room }: { room: RoomCardType }) {
  return (
    <a className="room-card" href={room.url} target="_blank" rel="noreferrer">
      <div className="room-card__thumb">
        {room.thumbnail ? (
          <img src={room.thumbnail} alt={room.title} loading="lazy" />
        ) : (
          <div className="room-card__thumb-placeholder">#{room.id}</div>
        )}
      </div>
      <div className="room-card__body">
        <div className="room-card__title">{room.title}</div>
        <div className="room-card__meta">
          <span>{formatVnd(room.price)}</span>
          <span>·</span>
          <span>{room.area}m²</span>
          <span>·</span>
          <span>{room.district}</span>
        </div>
        <div className="room-card__address">{room.address}</div>
        {room.distance_m != null && (
          <div className="room-card__distance">Cách {Math.round(room.distance_m)}m</div>
        )}
        <div className="room-card__id">#{room.id}</div>
      </div>
    </a>
  );
}
