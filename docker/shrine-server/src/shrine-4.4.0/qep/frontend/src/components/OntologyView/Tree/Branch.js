import React, { useState, useEffect, useContext } from "react";
import PropTypes from "prop-types";
import TreeItem from "@material-ui/lab/TreeItem";

import "./Branch.scss";
import { OntologyContext } from "components";
import { useBranch } from "./useBranch";

function Branch(props) {
  const { concept, nodeId, expandNode, collapseNode } = props;
  const { fetchChildren, shouldFetchChildren } = useContext(OntologyContext);
  const isCategory = !concept.path;
  const shouldRenderAsExpanded = isCategory;
  const [isExpanded, setIsExpanded] = useState(shouldRenderAsExpanded);
  const [children, setChildren] = useState([]);
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
    getIconByConceptType,
    DragPreviewImage,
    whitePixel,
    displayNameDiv
  } = useBranch({
    concept,
    isExpanded
  });

  const getChildren = () => {
    (async () => {
      const result = await fetchChildren(concept.path);
      if (Array.isArray(result.data)) {
        setChildren(result.data);
      }
    })();
  };

  const handleClick = () => {
    if (isParent) {
      if (shouldFetchChildren && !concept.children.length) {
        getChildren();
      }
      const newIsExpanded = !isExpanded;
      setIsExpanded(newIsExpanded);
      const newIcon = getIconByConceptType(concept.conceptType, newIsExpanded);
      setIcon(newIcon);
    }
  };

  useEffect(() => {
    if (isParent && concept.children.length) {
      setChildren(concept.children);
      setIsExpanded(shouldRenderAsExpanded);
      setIcon(
        getIconByConceptType(concept.conceptType, shouldRenderAsExpanded)
      );
    }
  }, [concept.children]);

  useEffect(() => {
    if (isExpanded) {
      expandNode(nodeId);
    } else {
      collapseNode(nodeId);
    }
  }, [isExpanded]);

  return (
    <>
      {!draggable && <DragPreviewImage connect={preview} src={whitePixel} />}
      <TreeItem
        nodeId={nodeId}
        onClick={handleClick}
        label={
          <div className="tree-item-label-container" ref={drag}>
            <div className="tree-item-icon">{icon}</div>
            {displayNameDiv}
          </div>
        }
        className={`Branch ${branchTypeClassName} ${
          isDragging ? "dragging" : ""
        }`}
        {...muiProps}
      >
        {children.map((child) => (
          <Branch
            {...props}
            concept={child}
            key={child.path}
            nodeId={child.path}
          />
        ))}
      </TreeItem>
    </>
  );
}

Branch.propTypes = {
  nodeId: PropTypes.string.isRequired,
  concept: PropTypes.shape({
    children: PropTypes.arrayOf(
      PropTypes.shape({
        path: PropTypes.string
      })
    ),
    path: PropTypes.string,
    displayName: PropTypes.string,
    highlightedName: PropTypes.string,
    conceptType: PropTypes.string.isRequired
  }).isRequired,
  expandNode: PropTypes.func.isRequired,
  collapseNode: PropTypes.func.isRequired
};

export default React.memo(Branch);
