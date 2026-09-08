import React, { useEffect } from "react";
import PropTypes from "prop-types";
import { useDrop } from "react-dnd";
import { Container } from "@material-ui/core";
import { Droppable } from "react-beautiful-dnd";

import { useScrollOnDataChange } from "hooks";
import { DRAGGABLE } from "models";
import "./DroppableConceptList.scss";

export const DroppableConceptList = ({
  list,
  listId,
  onConceptAddedFromOntology,
  children,
  isDropDisabled,
  onDropHover,
}) => {
  const scrollRef = useScrollOnDataChange({
    data: list.length,
    alignToBottom: false,
  });

  const [{ isOver }, drop] = useDrop({
    accept: [DRAGGABLE],
    drop: onConceptAddedFromOntology,
    collect: (monitor) => ({
      isOver: monitor.isOver(),
      canDrop: monitor.canDrop(),
    }),
  });

  return (
    <Droppable droppableId={listId} isDropDisabled={isDropDisabled}>
      {(provided, snapshot) => {
        const draggedOverClass =
          isOver || snapshot.isDraggingOver ? "dragged-over" : "";
        if (onDropHover) {
          onDropHover(isOver || snapshot.isDraggingOver);
        }
        return (
          <span ref={provided.innerRef} {...provided.droppableProps}>
            <Container
              ref={!isDropDisabled ? drop : null}
              className={`DroppableConceptList ${draggedOverClass}`}
            >
              {children}
              <div ref={scrollRef} />
            </Container>
            {provided.placeholder}
          </span>
        );
      }}
    </Droppable>
  );
};

DroppableConceptList.defaultProps = {
  onDropHover: null,
};

DroppableConceptList.propTypes = {
  list: PropTypes.arrayOf({}).isRequired,
  onConceptAddedFromOntology: PropTypes.func.isRequired,
  children: PropTypes.element.isRequired,
  shouldHighlight: PropTypes.bool.isRequired,
};
