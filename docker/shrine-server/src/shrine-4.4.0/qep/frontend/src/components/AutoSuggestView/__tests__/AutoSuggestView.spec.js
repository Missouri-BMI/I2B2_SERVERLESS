import React from "react";
import { mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { SearchSuggestions } from "models";
import { AutoSuggestView } from "..";
import { AutoSuggestContext } from "../AutoSuggestContext";

describe("AutoSuggestView ", () => {
  const autoSuggestions = SearchSuggestions({
    suggestions: [{ title: "mock suggestion title" }],
  });
  const onTextInputChange = jest.fn();
  const props = {
    autoSuggestions,
    onUpdateSuggestions: jest.fn(),
  };

  const output = mount(
    <AutoSuggestContext.Provider
      value={{
        onTextInputChange,
      }}
    >
      <AutoSuggestView {...props} />
    </AutoSuggestContext.Provider>
  );

  it("Should call not onUpdateSuggestions after a delay when the text is less than 3 characters", () => {
    output
      .find(".auto-suggest-input")
      .at(0)
      .simulate("change", { target: { value: "12" } });
    setTimeout(() => {
      expect(props.onUpdateSuggestions).toNotHaveBeenCalled();
    }, 500);
  });
  it("Should call onUpdateSuggestions after a delay when the text is greater than 3 characters", () => {
    output
      .find(".auto-suggest-input")
      .at(0)
      .simulate("change", { target: { value: "mock autosuggest search" } });
    setTimeout(() => {
      expect(props.onUpdateSuggestions).toHaveBeenCalled();
    }, 500);
  });
});
