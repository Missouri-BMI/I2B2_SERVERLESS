import { Timeline } from "../Timeline";
import {EventConstraintAnchor, EventConstraintBoundary, TimelineLink} from "../TimelineLink";

describe("Timeline model", () => {
  it("Should create a properly formatted Timeline object", () => {
    const expectedTimeline = {
      timelineEvents:[
        {
          concepts: [],
          containsDemographic: false,
          isExcluded: false,
          options: {
            startDate: null,
            endDate: null,
            occurrences: 1,
          },
        },
        {
          concepts: [],
          containsDemographic: false,
          isExcluded: false,
          options: {
            startDate: null,
            endDate: null,
            occurrences: 1,
          },
        }
      ],
      timelineLinks: [
        {
          BasicTimelineLink: {
            previousEventConstraint: {
              "anchor": EventConstraintAnchor.ANY,
              "boundary": EventConstraintBoundary.START
            },
            relationship: "Before",
            thisEventConstraint: {
              "anchor": EventConstraintAnchor.ANY,
              "boundary": EventConstraintBoundary.START
            },
            primaryTimeSpan: null,
            secondaryTimeSpan: null
          }
        }
      ],
    };

    const timelineLink = TimelineLink();
    const actualTimeline = Timeline({ timelineLink });
    expect(actualTimeline).toEqual(expectedTimeline);
  });
});
