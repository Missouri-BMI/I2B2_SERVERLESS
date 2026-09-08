import {
  selectSortBy,
  selectDataVersion,
  selectLimit,
  selectSkip,
  selectSearchResultsMetadata
} from "..";
import { defaultState } from "../../defaultState";

describe("selectors", () => {
  it("selectSortBy should select the sortBy element off of the application state", () => {
    const expected = defaultState.allQueries.sortBy;
    const actual = selectSortBy(defaultState);
    expect(actual).toEqual(expected);
  });

  it("selectDataVersion should select the dataVersion element off of the application state", () => {
    const expected = defaultState.selectedQuery.dataVersion;
    const actual = selectDataVersion(defaultState);
    expect(actual).toEqual(expected);
  });

  it("selectLimit should select the limit element off of the application state", () => {
    const expected = defaultState.allQueries.limit;
    const actual = selectLimit(defaultState);
    expect(actual).toEqual(expected);
  });

  it("selectSkip should select the skip element off of the application state", () => {
    const expected = defaultState.allQueries.skip;
    const actual = selectSkip(defaultState);
    expect(actual).toEqual(expected);
  });

  it("selectSearchResultsMetadata should select the metadata element off the application state", () => {
    const expected = defaultState.ontology.searchResultsMetadata;
    const actual = selectSearchResultsMetadata(defaultState);
    expect(actual).toEqual(expected);
  });
});
