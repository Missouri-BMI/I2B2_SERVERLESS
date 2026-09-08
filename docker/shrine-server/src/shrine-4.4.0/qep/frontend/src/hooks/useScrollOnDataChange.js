import React, { useEffect } from 'react';

export function useScrollOnDataChange({
  data,
  alignToBottom = true
}) {
  const scrollToBottomRef = React.useRef(null);
  const scrollToBottom = () => scrollToBottomRef.current.scrollIntoView({
    behavior: "smooth",
    ...!alignToBottom && { block: "nearest", inline: "nearest" }
  });
  useEffect(scrollToBottom, [data]);

  return scrollToBottomRef;
}
