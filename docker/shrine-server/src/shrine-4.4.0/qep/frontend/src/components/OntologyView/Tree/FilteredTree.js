import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import TreeView from "@material-ui/lab/TreeView";
import Typography from "@material-ui/core/Typography";

import { List, AutoSizer, CellMeasurerCache } from "react-virtualized";

import { useInfiniteScroll } from "hooks";
import { FilteredBranch } from "./FilteredBranch";

import "./Tree.scss";
import "./FilteredTree.scss";

const cache = new CellMeasurerCache({
  fixedWidth: true,
  defaultHeight: 100,
});

export const FilteredTree = ({ data, onScrollEnd, searchQuery }) => {
  const defaultSelectedConceptId = "";
  const [closedConceptIds, setClosedConceptIds] = useState([]);
  const [selectedConcept, setSelectedConcept] = useState(
    defaultSelectedConceptId
  );
  const [scrollToIndex, setScrollToIndex] = useState(undefined);
  const [displayedConcepts, setDisplayedConcepts] = useState([]);
  const [nextPageIndex, setNextPageIndex] = useState(0);
  const listRef = React.useRef();
  const scrollRef = React.useRef();

  const flattenExpandedConcept = (node, depth, result) => {
    const {
      displayName,
      highlightedName = null,
      conceptType,
      conceptCategory = null,
      isActive,
      children,
    } = node;
    const hasChildren = children && Object.values(children).length > 0;
    const path = node.path || node.displayName;
    const collapsed = closedConceptIds.includes(path);

    result.push({
      displayName,
      highlightedName,
      isActive,
      path,
      conceptType,
      conceptCategory,
      hasChildren,
      depth,
      collapsed,
      ...node,
    });

    if (!collapsed && children) {
      Object.values(children).forEach((child) => {
        flattenExpandedConcept(child, depth + 1, result);
      });
    }
  };

  const flattenExpandedConceptsForDisplayInList = (treeData, result = []) => {
    Object.values(treeData).forEach((node) => {
      flattenExpandedConcept(node, 1, result);
    });
    return result;
  };

  const resetListDimensions = () => {
    cache.clearAll();
    if (listRef.current) {
      listRef.current.recomputeRowHeights();
    }
  };

  const handleScrollReachedBottom = () => {
    setSelectedConcept(defaultSelectedConceptId);
    onScrollEnd();
  };

  const { setScrollReference } = useInfiniteScroll({
    onScrollReachedBottom: handleScrollReachedBottom,
    scrollRef,
  });

  useEffect(() => {
    const flattenedConcepts = flattenExpandedConceptsForDisplayInList(data);
    setNextPageIndex(
      displayedConcepts.length === 0 ? 0 : displayedConcepts.length - 1
    );
    setDisplayedConcepts(flattenedConcepts);
    resetListDimensions();
  }, [data, closedConceptIds]);

  useEffect(() => {
    const result = displayedConcepts.findIndex(
      (item) => item.path === selectedConcept
    );
    setScrollToIndex(result !== -1 ? result : nextPageIndex);
  }, [displayedConcepts]);

  useEffect(() => {
    if (!scrollRef.current) {
      const scrollBarReference = document.getElementsByClassName(
        "ReactVirtualized__List"
      )[0];
      if (scrollBarReference) {
        scrollRef.current = scrollBarReference;
        setScrollReference(scrollRef.current);
      }
    }
  });

  const toggleExpanded = (node) => {
    setSelectedConcept(node.path);
    return !node.collapsed
      ? setClosedConceptIds([...closedConceptIds, node.path])
      : setClosedConceptIds(
          closedConceptIds.filter((path) => path !== node.path)
        );
  };

  const renderRow = ({ key, style, parent, index }) => {
    const node = displayedConcepts[index];
    return (
      <FilteredBranch
        key={key}
        style={style}
        parent={parent}
        index={index}
        concept={node}
        toggleExpanded={toggleExpanded}
        cache={cache}
      />
    );
  };

  const noResultsMarkup = searchQuery.hasSearched && (
    <div className="no-results-message">
      <div className="query-error-message">
        <Typography>
          Your search -<b>{` ${searchQuery.inputValue} `}</b>
          filtered by
          <b>{` ${searchQuery.filter.filterValue} `}</b>- did not match any
          concepts.
        </Typography>
      </div>
      <div className="suggestions">
        <Typography>Suggestions:</Typography>
        <ul>
          <li>
            <Typography>
              Make sure all keywords are spelled correctly.
            </Typography>
          </li>
          <li>
            <Typography>Select keywords from the autosuggest list.</Typography>
          </li>
          <li>
            <Typography>Try different keywords.</Typography>
          </li>
          <li>
            <Typography>Filter by ‘All Concepts’.</Typography>
          </li>
          <li>
            <Typography>Avoid special characters such as {`; - , : ^ / [ ] ( ) % & . ' = < > | * + _ ! # @ $ ~ ? \``}</Typography>
          </li>
        </ul>
      </div>
    </div>
  );

  const hasNoResults = Object.keys(data).length === 0;
  return hasNoResults ? (
    noResultsMarkup
  ) : (
    <div className="FilteredTree">
      <div className="list">
        <AutoSizer onResize={resetListDimensions}>
          {({ width, height }) => (
            <TreeView className="Tree" style={{ width: `${width}px` }}>
              <List
                ref={listRef}
                scrollToIndex={scrollToIndex}
                width={width}
                height={height}
                deferredMeasurementCache={cache}
                rowHeight={cache.rowHeight}
                rowRenderer={renderRow}
                rowCount={displayedConcepts.length}
                overscanRowCount={3}
                fData={displayedConcepts}
              />
            </TreeView>
          )}
        </AutoSizer>
      </div>
    </div>
  );
};

FilteredTree.propTypes = {
  data: PropTypes.shape({}).isRequired,
  onScrollEnd: PropTypes.func.isRequired,
  searchQuery: PropTypes.shape({
    inputValue: PropTypes.string,
    filter: PropTypes.shape({ filterValue: PropTypes.string }),
    hasSearched: PropTypes.bool.isRequired,
  }).isRequired,
};
