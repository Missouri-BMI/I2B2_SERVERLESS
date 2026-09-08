import React, {useState} from "react";
import PropTypes from "prop-types";
import {Button, Icon} from "@material-ui/core";
import "./EventViewAdder.scss";

export default function EventViewAdder({addTimelineEvent, maxEventsReached}) {

  return (
    !maxEventsReached() && <div className="EventViewAdder">
      <Button className="addTEvent" onClick={addTimelineEvent}>
        <Icon className="fa fa-plus-circle" />
        <label> Add Event </label>
      </Button>
    </div>
  );
}

EventViewAdder.propTypes = {
  addTimelineEvent: PropTypes.func.isRequired,
  maxEventsReached: PropTypes.func.isRequired,
};
