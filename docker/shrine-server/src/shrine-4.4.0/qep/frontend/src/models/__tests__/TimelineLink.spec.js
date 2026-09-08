import {
  EventConstraint,
  TimelineLink,
  TimeSpan,
  EventConstraintAnchor,
  EventConstraintBoundary,
  RelationshipTypes,
  TimeSpanOperatorTypes,
  TimeSpanUnitTypes,
} from "../TimelineLink";

describe("TimelineLink should be properly formatted", () => {
  it("should match the expected JSON format", () => {
    const expected = {
      BasicTimelineLink: {
        previousEventConstraint: EventConstraint({
          "anchor": EventConstraintAnchor.LAST,
          "boundary": EventConstraintBoundary.END
        }),
        thisEventConstraint: EventConstraint({
          "anchor": EventConstraintAnchor.ANY,
          "boundary": EventConstraintBoundary.START
        }),
        relationship: RelationshipTypes.BEFORE,
        primaryTimeSpan: {
          operator: TimeSpanOperatorTypes.GREATEREQUAL,
          value: 0,
          unit: TimeSpanUnitTypes.DAY
        },
        secondaryTimeSpan: {
          operator: TimeSpanOperatorTypes.GREATEREQUAL,
          value: 0,
          unit: TimeSpanUnitTypes.DAY
        }
      },
    };
    const timelineLink = TimelineLink({
      thisEventConstraint: EventConstraint({boundary: EventConstraintBoundary.START , anchor: EventConstraintAnchor.ANY}),
      previousEventConstraint: EventConstraint({boundary: EventConstraintBoundary.END , anchor: EventConstraintAnchor.LAST}),
      relationship: "Before",
      primaryTimeSpan: TimeSpan({ operator: TimeSpanOperatorTypes.GREATEREQUAL, value: 0, units: TimeSpanUnitTypes.DAY}),
      secondaryTimeSpan: TimeSpan({ operator: TimeSpanOperatorTypes.GREATEREQUAL, value: 0, units: TimeSpanUnitTypes.DAY})
    });
    expect(JSON.stringify(timelineLink)).toEqual(JSON.stringify(expected));
  });

  it("should match the expected JSON format with the *default* timespan", () => {
    const expected = {
      BasicTimelineLink: {
        previousEventConstraint: {
          "anchor": EventConstraintAnchor.ANY,
          "boundary": EventConstraintBoundary.START
        },
        relationship: RelationshipTypes.BEFORE,
        thisEventConstraint: {
          "anchor": EventConstraintAnchor.ANY,
          "boundary": EventConstraintBoundary.START
        },
        primaryTimeSpan: null,
        secondaryTimeSpan: null
      },
    };
    const timelineLink = TimelineLink({
      previousEventConstraint: EventConstraint({anchor:EventConstraintAnchor.ANY, boundary: EventConstraintBoundary.START}),
      thisEventConstraint: EventConstraint({anchor:EventConstraintAnchor.ANY, boundary: EventConstraintBoundary.START}),
      relationship: RelationshipTypes.BEFORE,
    });

    expect(timelineLink).toEqual(expected);
  });
});
