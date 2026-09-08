import React, { useState } from "react";
import { DragPreviewImage, useDrag } from "react-dnd";
import AddBoxOutlinedIcon from "@material-ui/icons/AddBoxOutlined";
import IndeterminateCheckBoxOutlinedIcon from "@material-ui/icons/IndeterminateCheckBoxOutlined";
import FiberManualRecordOutlinedIcon from "@material-ui/icons/FiberManualRecordOutlined";
import {Icon, Popper} from "@material-ui/core";

import { DRAGGABLE, NOT_DRAGGABLE } from "models";
import whitePixel from "./white.png";
import "./Branch.scss";

const iconStyles = {
  fontSize: "1rem",
  marginBottom: "-2px",
};

const containerCollapse = (
  <IndeterminateCheckBoxOutlinedIcon style={iconStyles} />
);
const containerExpand = <AddBoxOutlinedIcon style={iconStyles} />;
const folderCollapse = (
  <Icon
    className="fa fa-folder-open-o"
    style={{ fontSize: "inherit", overflow: "inherit" }}
  />
);
const folderExpand = (
  <Icon
    className="fa fa-folder-o"
    style={{ fontSize: "inherit", overflow: "inherit" }}
  />
);
const leafIcon = (
  <FiberManualRecordOutlinedIcon
    style={{
      fontSize: "0.7rem",
      margin: "2px 0px 0px 2px",
      verticalAlign: "inherit",
    }}
  />
);

const getIconByConceptType = (conceptType, isExpanded) => {
  switch (conceptType) {
    case "Container": {
      return isExpanded ? containerCollapse : containerExpand;
    }
    case "Folder": {
      return isExpanded ? folderCollapse : folderExpand;
    }
    default: {
      return leafIcon;
    }
  }
};

const conceptTypeOptions = (conceptType, isActive) => {
  switch (conceptType) {
    case "Container":
      return {
        draggable: false,
        isParent: true,
        active: isActive,
      };
    case "Folder":
      return {
        draggable: isActive,
        isParent: true,
        active: isActive,
      };
    default:
      return {
        draggable: isActive,
        isParent: false,
        active: isActive,
      };
  }
};

const getDashes = (depth, style) => {
  let left = 16;
  const result = [];
  for (let i = 2; i <= depth; i += 1) {
    result.push(
      <div
        style={{
          height: `${style.height}px`,
          borderLeft: "1px dashed",
          left: `${left}px`,
          position: "absolute",
        }}
      />
    );
    left += 16;
  }

  return result;
};

export const getBranchTypeClassName = (draggable, active) =>
  (draggable ? "draggable-concept" : "non-draggable-concept") +
  (active ? "" : " inactive-concept");

export const getDisplayNameDiv = (concept) =>
  concept.highlightedName ? (
    <div
      className="display-name"
      dangerouslySetInnerHTML={{ __html: concept.highlightedName }}
    />
  ) : (
    <div className="display-name">{concept.displayName}</div>
  );

export function useBranch({ concept, isExpanded, allowDrag = true }) {
  const [isDragging, setDragging] = useState(false);
  const [icon, setIcon] = useState(
    getIconByConceptType(concept.conceptType, isExpanded)
  );
  const [conceptInfoOpen, setConceptInfoOpen] = useState(false);
  const [anchorEl, setAnchorEl] = React.useState(null);

  const { draggable, isParent, active, ...muiProps } = conceptTypeOptions(
    concept.conceptType,
    concept.isActive
  );
  const branchTypeClassName =
    (draggable ? "draggable-concept" : "non-draggable-concept") +
    (active ? "" : " inactive-concept");

  const [, drag, preview] = allowDrag
    ? useDrag({
        item: { data: concept, type: draggable ? DRAGGABLE : NOT_DRAGGABLE },
        begin: () => setDragging(true),
        end: () => setDragging(false),
      })
    : [];

  const handlePopoverOpen = (event) => {
    setAnchorEl(event.currentTarget);
    setConceptInfoOpen(true);
  };

  const handlePopoverClose = (event) => {
    setConceptInfoOpen(false);
    event.stopPropagation();
  };

  const displayNameDiv = concept.highlightedName ? (
    <div>
      <div className="display-name">
        <span
          dangerouslySetInnerHTML={{ __html: concept.highlightedName }}
        />
        {concept.metadata ? <div className="fa fa-info-circle info metadata-icon" onMouseOver={handlePopoverOpen} onMouseLeave={handlePopoverClose}/> : ""}
      </div>
      <Popper
        className="metadata-popper"
        open={conceptInfoOpen}
        anchorEl={anchorEl}
        onClose={handlePopoverClose}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "left",
        }}
        transformOrigin={{
          vertical: "top",
          horizontal: "left",
        }}
      >
        <div className="metadata">
          {concept.metadata}
        </div>
      </Popper>
    </div>
  ) : (
    <div className="display-name"  >
      {concept.displayName} {concept.metadata ? <span className="fa fa-info-circle info info-icon" onMouseOver={handlePopoverOpen} onMouseLeave={handlePopoverClose}/> : ""}
      <Popper
        className="metadata-popper"
        open={conceptInfoOpen}
        anchorEl={anchorEl}
        onClose={handlePopoverClose}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "left",
        }}
        transformOrigin={{
          vertical: "top",
          horizontal: "left",
        }}
      >
        <div className="metadata">
          {concept.metadata}
          </div>
      </Popper>
    </div>
  );

  return {
    icon,
    setIcon,
    isDragging,
    setDragging,
    draggable,
    isParent,
    active,
    muiProps,
    branchTypeClassName,
    drag,
    preview,
    getIconByConceptType,
    DragPreviewImage,
    whitePixel,
    displayNameDiv,
  };
}
