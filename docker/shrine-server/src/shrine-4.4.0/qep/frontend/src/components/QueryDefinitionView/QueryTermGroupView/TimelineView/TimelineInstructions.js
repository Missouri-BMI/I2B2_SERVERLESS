import React from "react";
import "./TimelineInstructions.scss";

export const TimelineInstructions = ({ eventId, isOptional }) =>
  (
    <div className="create-event">
      drag a concept here to define event {eventId} in the sequence
      {isOptional && <span> (optional)</span>}
    </div>
  );
