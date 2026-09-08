import PropTypes from "prop-types";

export const RelationshipTypes = {
  BEFORE: "Before",
  BEFOREORSIMULTANEOUS: "BeforeOrSimultaneous",
  SIMULTANEOUS: "Simultaneous"
}

export const TimeSpanOperatorTypes = {
  GREATER : "GREATER",
  GREATEREQUAL : 'GREATEREQUAL',
  EQUAL : "EQUAL",
  LESSEQUAL : "LESSEQUAL",
  LESS: "LESS"
}

export const TimeSpanUnitTypes = {
  DAY: "DAY",
  MONTH: "MONTH",
  YEAR: "YEAR"
}

export const EventConstraintAnchor = {
  FIRST: "FIRST",
  ANY: "ANY",
  LAST: "LAST"
}

export const EventConstraintBoundary = {
  START: "START",
  END: "END",
}

export const EventConstraint = ({
                                  anchor = "ANY",
                                  boundary = "START",
                                } = {}) => ({
  anchor,
  boundary,
});

EventConstraint.propTypes = {
  anchor: PropTypes.string,
  boundary: PropTypes.string,
};

// TODO: constant values here are taken from "ENUM" in TimelineLinkView.js make that clear
export const TimeSpan = ({
  operator = TimeSpanOperatorTypes.GREATEREQUAL,
  value = 0,
  unit = TimeSpanUnitTypes.DAY
} = {}) => ({
  operator,
  value,
  unit
})

TimeSpan.propTypes = {
  operator: PropTypes.string,
  value: PropTypes.number,
  unit: PropTypes.string
}

export const TimelineLink = ({
  previousEventConstraint = EventConstraint(),
  thisEventConstraint = EventConstraint(),
  relationship = RelationshipTypes.BEFORE,
  primaryTimeSpan = null,
  secondaryTimeSpan = null
} = {}) => ({
  BasicTimelineLink: {
    previousEventConstraint,
    thisEventConstraint,
    relationship,
    primaryTimeSpan,
    secondaryTimeSpan
   }
});

TimelineLink.propTypes = {
  previousEventConstraint: PropTypes.shape(EventConstraint.propTypes),
  thisEventConstraint: PropTypes.shape(EventConstraint.propTypes),
  relationship: PropTypes.string,
  primaryTimeSpan: PropTypes.shape(TimeSpan.propTypes),
  secondaryTimeSpan: PropTypes.shape(TimeSpan.propTypes)
};
