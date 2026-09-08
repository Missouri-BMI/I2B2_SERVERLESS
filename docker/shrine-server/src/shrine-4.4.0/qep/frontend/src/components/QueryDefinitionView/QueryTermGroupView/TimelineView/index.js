import React, {useEffect, useState} from "react";
import PropTypes from "prop-types";
import {TimelineEvent, TimelineLink} from "models";
import { TimelineLinkView } from "./TimelineLinkView";
import EventView from "./EventView";
import "./TimelineView.scss";
import EventViewAdder from "./EventViewAdder";
import {local} from "../../../../utilities";

export default function TimelineView(props) {
  const maxEvents = 3;
  const [numEvents, setNumEvents] = useState(props.group.timeline.timelineEvents.length);

  const increaseMaxEvents = () => {
    setNumEvents(numEvents+1);
  };

  const maxEventsReached = () => {
    return numEvents === maxEvents;
  };

  const insertTimelineEvent = (index) => {
    let timelineEvent = TimelineEvent();
    if(index >= props.group.timeline.timelineEvents.length) {
      props.group.timeline.timelineEvents.push(timelineEvent);
    }else{
      timelineEvent = props.group.timeline.timelineEvents[index];
    }
    return timelineEvent;
  };

  const insertTimelineLink = (index) => {
    let timelineLink = TimelineLink();

    if(index >= props.group.timeline.timelineLinks.length) {
      props.group.timeline.timelineLinks.push(timelineLink);
    }else{
      timelineLink = props.group.timeline.timelineLinks[index];
    }
    return timelineLink;
  };

  useEffect(() => {
    let newNumEvents = props.group.timeline.timelineEvents.length > maxEvents ? maxEvents : props.group.timeline.timelineEvents.length;
    setNumEvents(newNumEvents);
  }, [props.group.timeline.timelineEvents]);

  return (
    <div className="TimelineView">
      {[...Array(numEvents)].map((x, i) => {
          return (
            <div>
            <EventView
            event={insertTimelineEvent(i)}
            eventId={i}
            updateTimelineEventConcepts={props.updateTimelineEventConcepts}
            updateTimelineEventOptions={props.updateTimelineEventOptions}
            fetchConceptInfo={props.fetchConceptInfo}
            updateTerm={props.updateTerm}
            header={"Event " + (i+1)}
            optional = {i >=2} //min of 2 events are required
          />
          {
            i < numEvents - 1 && <TimelineLinkView
            timelineLink={insertTimelineLink(i)}
            event1Index={i}
            event2Index={i+1}
            updateTimelineLink={props.updateQueryGroupTimelineLink}
            />
          }
        </div>
      )})}
      <EventViewAdder  addTimelineEvent={increaseMaxEvents} maxEventsReached={maxEventsReached} />
    </div>
  );
}
