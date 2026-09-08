import PropTypes from "prop-types";

import { TimelineEvent } from "./TimelineEvent";
import { TimelineLink } from "./TimelineLink";

export const isTimelineEvent = (id) =>
   id.startsWith("event")

export const Timeline = ({
  timelineEvents= [
    TimelineEvent(), TimelineEvent()
  ],
  timelineLinks = [
    TimelineLink()
  ],
} = {}) => ({
  timelineEvents,
  timelineLinks,
});

Timeline.propTypes = {
  events: PropTypes.arrayOf(PropTypes.shape(TimelineEvent.propTypes)),
  timelineLinks: PropTypes.arrayOf(PropTypes.shape(TimelineEvent.propTypes)),
};
