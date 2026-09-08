import React from "react";
import PropTypes from "prop-types";
import { Card } from "@material-ui/core";

import InclusionExclusionPanel from "./InclusionExclusionPanel";
import TimelineView from "./TimelineView";
import { QueryTermGroupStatusTypes } from "models";
import "./GroupBody.scss";

export default function GroupBody(props) {
  const { hasChildren, activeClass, status } = props;
  const hasChildrenClass = hasChildren ? "has-children" : "no-children";
  const isInclusionClass =
    status !== QueryTermGroupStatusTypes.TIMELINE
      ? "inclusion-group"
      : "event-group";

  return (
    <Card
      className={`GroupBody ${activeClass} ${hasChildrenClass} ${isInclusionClass}`}
    >
      <div />
      {status !== QueryTermGroupStatusTypes.TIMELINE ? (
        <InclusionExclusionPanel {...props} />
      ) : (
        <TimelineView {...props} />
      )}
    </Card>
  );
}

GroupBody.propTypes = {
  activeClass: PropTypes.string.isRequired,
  hasChildren: PropTypes.bool.isRequired,
  status: PropTypes.string.isRequired,
};
