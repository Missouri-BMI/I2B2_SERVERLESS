import React, { useState, useEffect, useRef } from "react";
import PropTypes from "prop-types";
import { MenuItem, Select, Grid, Typography, Divider } from "@material-ui/core";

import {
  Loader,
  FetchError,
  AutoSuggest,
  OntologyContext,
  AutoSuggestContext,
} from "components";
import { Ontology } from "models";
import { Tree, FilteredTree } from "./Tree";
import "./OntologyView.scss";

export function OntologyView(props) {
  const { ontology, fetchChildren, onSearch, onSearchReset } = props;
  const [filter, setFilter] = useState("");
  const [inputValue, setInputValue] = useState("");
  const inputValueRef = useRef(inputValue);
  const [optionElements, setOptionElements] = useState([]);
  const ontologyRef = useRef(ontology);
  const isFiltered = ontology.filteredTree !== null;
  const [previousSearchData, setPreviousSearchData] = useState({
    hasSearched: false,
  });
  const canSearch =
    inputValue.length > 2 &&
    (previousSearchData.hasSearched === false ||
      ontology.textFilter !== inputValue ||
      previousSearchData.filter !== filter);

  const createOptionElements = () =>
    ontology.filterOptions.reduce((acc, curOption) => {
      const { filterType, filterValue, displayableName } = curOption;
      const menuItem = (
        <MenuItem
          key={filterValue}
          value={filterValue}
          classes={{ root: "filter-dropdown-option" }}
        >
          {displayableName}
        </MenuItem>
      );
      if (filterType === "CODE_CATEGORY") {
        acc.push(<Divider variant="middle" key={`${filterValue}-divider`} />);
      }
      acc.push(menuItem);
      return acc;
    }, []);

  const getFilterData = () => {
    // O(N) on filter options, should be okay since only expecting ~10 filter options
    const filterType = ontology.filterOptions.reduce(
      (acc, { filterType, filterValue }) => {
        return acc || (filter === filterValue ? filterType : acc);
      },
      false
    );

    return { filterValue: filter, filterType };
  };

  const loadMatches = () => {
    if (ontologyRef.current.canLoadMoreFilteredResults) {
      const filterData = getFilterData();
      onSearch(inputValueRef.current, filterData);
    }
  };

  const handleFilterChange = (event) => setFilter(event.target.value);

  const handleSubmit = (e) => {
    e.stopPropagation();
    e.preventDefault();
    if (canSearch) {
      const filterData = getFilterData();
      setPreviousSearchData({
        inputValue,
        filter: filterData,
        hasSearched: true,
      });
      onSearch(inputValueRef.current, filterData);
    }
  };

  useEffect(() => {
    if (ontology.filterOptions) {
      if (ontology.filterOptions.length !== 0) {
        setFilter(ontology.filterOptions[0].filterValue);
      }
      setOptionElements(createOptionElements());
    }
  }, [ontology.filterOptions]);

  useEffect(() => {
    ontologyRef.current = ontology;
  }, [ontology]);

  useEffect(() => {
    if (inputValue.length === 0 && ontology.root.length) {
      onSearchReset();
    }
    inputValueRef.current = inputValue;
  }, [inputValue]);

  return (
    <div className="OntologyView tutorialStep1">
      <Grid container className="title-grid">
        <Typography className="concepts-title">
          Medical Concepts List
        </Typography>
      </Grid>
      <form
        onSubmit={handleSubmit}
        className="content-wrapper"
        style={isFiltered ? { overflow: "hidden" } : null}
      >
        <div className="dropdown-grid">
          <div>
            <Typography className="filter-label">Search:</Typography>
          </div>
          <div>
            <Select
              value={filter}
              className="filter-dropdown"
              onChange={handleFilterChange}
            >
              {optionElements.length && optionElements}
            </Select>
          </div>
        </div>
        <input type="submit" style={{ display: "none" }} />
        <AutoSuggestContext.Provider
          value={{
            onTextInputChange: setInputValue,
            canSearch,
          }}
        >
          <AutoSuggest />
        </AutoSuggestContext.Provider>
        {ontology.isFetching && <Loader />}
        {ontology.error.hasError ? (
          <FetchError error={ontology.error} />
        ) : (
          <OntologyContext.Provider
            value={{
              fetchChildren,
              shouldFetchChildren: !isFiltered,
            }}
          >
            {isFiltered ? (
              <FilteredTree
                data={ontology.filteredTree}
                onScrollEnd={loadMatches}
                isLoading={ontology.isFetching}
                totalHits={ontology.totalHits}
                searchQuery={previousSearchData}
              />
            ) : (
              <Tree
                conceptList={ontology.root}
                isLoading={ontology.isFetching}
              />
            )}
          </OntologyContext.Provider>
        )}
      </form>
    </div>
  );
}

OntologyView.propTypes = {
  ontology: PropTypes.shape(Ontology.propTypes).isRequired,
  fetchChildren: PropTypes.func.isRequired,
  onSearch: PropTypes.func.isRequired,
  onSearchReset: PropTypes.func.isRequired,
};
