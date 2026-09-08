import React from "react";
import PropTypes from "prop-types";
import TreeItem from "@material-ui/lab/TreeItem";

import "./Branch.scss";
import { getBranchTypeClassName, useBranch } from "./useBranch";

function StaticBranch(props) {
  const { concept, nodeId } = props;
  const isExpanded = concept.children && concept.children.length > 0;
  const { muiProps, getIconByConceptType, displayNameDiv } = useBranch({
    concept,
    isExpanded,
    allowDrag: false
  });

  return (
    <>
      <TreeItem
        nodeId={nodeId}
        label={
          <div className="tree-item-label-container">
            <div className="tree-item-icon">
              {getIconByConceptType(concept.conceptType, isExpanded)}
            </div>
            {displayNameDiv}
          </div>
        }
        className={`Branch ${getBranchTypeClassName(false, concept.isActive)}`}
        {...muiProps}
      >
        {concept.children &&
          concept.children.map((child) => (
            <StaticBranch
              concept={child}
              key={`${child.conceptType}-${child.displayName}`}
              nodeId={`${child.conceptType}-${child.displayName}`}
            />
          ))}
      </TreeItem>
    </>
  );
}

StaticBranch.propTypes = {
  nodeId: PropTypes.string.isRequired,
  concept: PropTypes.shape({
    children: PropTypes.arrayOf(
      PropTypes.arrayOf(
        PropTypes.shape({
          path: PropTypes.string
        })
      )
    ),
    path: PropTypes.string,
    displayName: PropTypes.string,
    highlightedName: PropTypes.string,
    isActive: PropTypes.bool,
    conceptType: PropTypes.string.isRequired
  }).isRequired
};

export default React.memo(StaticBranch);
