import React, { useState, useEffect, useMemo, useContext } from "react";
import PropTypes from "prop-types";
import { Grid, Button, CircularProgress } from "@material-ui/core";
import { Autocomplete } from "@material-ui/lab/";

import throttle from "lodash.throttle";
import { SearchSuggestions } from "models";
import { ValidatedTextField } from "components";
import { AutoSuggestContext } from "./AutoSuggestContext";
import "./AutoSuggestView.scss";

export function AutoSuggestView({ autoSuggestions, onUpdateSuggestions }) {
  const [inputText, setInputText] = useState("");
  const [options, setOptions] = useState([]);
  const [open, setOpen] = useState(false);
  const { onTextInputChange, canSearch } = useContext(AutoSuggestContext);
  const [isValidInputText, setIsValidInputText] = useState(false);

  const update = useMemo(
    () =>
      throttle(
        (input) => {
          onUpdateSuggestions(input);
        },
        500,
        { leading: false, trailing: true }
      ),
    []
  );

  const enableSearch = canSearch && isValidInputText;

  const handleSearchChange = (event, value) => {
    setInputText(value);
  };

  const closePopupOnEnter = (event) => {
    if (event.key === "Enter") {
      setOpen(false);
    }
  };

  const onTextChange = (textValue, textValid) => {
    if (textValid) {
      setIsValidInputText(true);
    } else {
      setIsValidInputText(false);
    }
  };

  useEffect(() => {
    setOptions(autoSuggestions.suggestions);
  }, [autoSuggestions.suggestions]);

  useEffect(() => {
    onTextInputChange(inputText);
    if (isValidInputText && inputText.length >= 3) {
      update(inputText);
    } else {
      setOpen(false);
      setOptions([]);
    }
  }, [inputText]);

  return (
    <div className="AutoSuggestView">
      <Grid container className="search-grid">
        <Grid item xs={9} className="search-text-field">
          <Autocomplete
            freeSolo
            open={open}
            onOpen={() => {
              setOpen(true);
            }}
            filterOptions={(opts) => opts}
            onClose={() => {
              setOpen(false);
            }}
            noOptionsText="no matches"
            options={options}
            getOptionLabel={(option) =>
              option.suggestion ? option.suggestion : inputText
            }
            renderOption={(option) => (
              <div>
                {option.suggestion}
                <span className="occurrences">
                  {option.occurrences
                    .toString()
                    .replace(/\B(?=(\d{3})+(?!\d))/g, ",")}{" "}
                  concepts
                </span>
              </div>
            )}
            loading={autoSuggestions.isFetching}
            onInputChange={handleSearchChange}
            onKeyDown={closePopupOnEnter}
            renderInput={(params) => (
              <ValidatedTextField
                fullWidth
                {...params}
                className="auto-suggest-input"
                label="begin typing criteria"
                margin="normal"
                InputProps={{
                  ...params.InputProps,
                  endAdornment: (
                    <>
                      {autoSuggestions.isFetching ? (
                        <CircularProgress color="inherit" size={20} />
                      ) : null}
                      {params.InputProps.endAdornment}
                    </>
                  ),
                }}
                onTextChange={onTextChange}
                required={false}
                invalidCharsRegex="[\!\`\;\:\^\/\%\&\=\<\>\|\*\+\_\@\#\$\~\?]"
              />
            )}
          />
        </Grid>
        <Grid item xs={2} className="button-field">
          <Button
            disabled={!enableSearch}
            variant="outlined"
            size="small"
            type="submit"
            className={`go-button ${enableSearch ? "active" : ""}`}
          >
            Go
          </Button>
        </Grid>
      </Grid>
    </div>
  );
}

AutoSuggestView.propTypes = {
  autoSuggestions: PropTypes.shape(SearchSuggestions.propTypes).isRequired,
  onUpdateSuggestions: PropTypes.func.isRequired,
};
