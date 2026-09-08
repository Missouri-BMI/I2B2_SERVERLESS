import React from "react";
import PropTypes from "prop-types";
import TreeItem from "@material-ui/lab/TreeItem";

import { CellMeasurer } from "react-virtualized";

import "./Branch.scss";
import "./FilteredTree.scss";

import { useBranch } from "./useBranch";

const leftIncrement = 20;
const drawDashes = (depth, style) => {
  let left = leftIncrement;
  const result = [];
  for (let i = 2; i <= depth; i += 1) {
    result.push(
      <div
        style={{
          height: `${style.height - 2}px`,
          borderLeft: "1px dashed rgba(0, 0, 0, .15)",
          left: `${left - 8}px`,
          position: "absolute"
        }}
      />
    );
    left += leftIncrement;
  }

  return result;
};

export const FilteredBranch = ({
  concept,
  key,
  style,
  parent,
  index,
  toggleExpanded,
  cache
}) => {
  const {
    icon,
    setIcon,
    isDragging,
    draggable,
    isParent,
    muiProps,
    branchTypeClassName,
    drag,
    preview,
    DragPreviewImage,
    whitePixel,
    displayNameDiv,
    getIconByConceptType
  } = useBranch({
    concept,
    isExpanded: !concept.collapsed
  });

  React.useEffect(() => {
    setIcon(getIconByConceptType(concept.conceptType, !concept.collapsed));
  });

  const left = (concept.depth - 1) * leftIncrement - 8;
  const handleClick = () => {
    if (isParent) {
      toggleExpanded(concept);
    }
  };
  return (
    <CellMeasurer
      key={key}
      cache={cache}
      parent={parent}
      columnIndex={0}
      rowIndex={index}
    >
      <div style={style} className="Branch row">
        {concept.depth > 1 && drawDashes(concept.depth, style)}
        <div
          className="content"
          style={{
            position: "relative",
            left: `${left}px`,
            overflowWrap: "break-word",
            overflow: "hidden",
            width: `calc(100% - ${left}px)`,
            marginRight: "20px",
            marginLeft: "8px",
            marginTop: "4px"
          }}
        >
          {!draggable && (
            <DragPreviewImage connect={preview} src={whitePixel} />
          )}
          <TreeItem
            nodeId={concept.path}
            label={
              <div
                style={{ display: "flex" }}
                onClick={handleClick}
                role="button"
              >
                {!concept.collapsed && concept.hasChildren && (
                  <div
                    style={{
                      height: `${style.height - 2}px`,
                      left: `${leftIncrement - 86}px`,
                      position: "absolute",
                      borderLeft: "1px dashed rgba(0, 0, 0, .15)",
                      marginTop: "32px"
                    }}
                  />
                )}
                <div ref={drag} className="tree-item-label-container">
                  <div className="tree-item-icon">{icon}</div>
                  {displayNameDiv}
                </div>
              </div>
            }
            className={`Branch ${branchTypeClassName} ${
              isDragging ? "dragging" : ""
            }`}
            {...muiProps}
          />
        </div>
      </div>
    </CellMeasurer>
  );
};

FilteredBranch.propTypes = {
  concept: PropTypes.shape({
    path: PropTypes.string,
    depth: PropTypes.number,
    collapsed: PropTypes.bool,
    hasChildren: PropTypes.bool,
    conceptType: PropTypes.string
  }).isRequired,
  style: PropTypes.shape({
    height: PropTypes.number
  }).isRequired,
  key: PropTypes.string.isRequired,
  parent: PropTypes.shape({}).isRequired,
  index: PropTypes.number.isRequired,
  toggleExpanded: PropTypes.func.isRequired,
  cache: PropTypes.shape({}).isRequired
};
