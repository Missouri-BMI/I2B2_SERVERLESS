import { useEffect, useState, useRef } from "react";

export function useScrollOnDrag({
  scrollBy = window.scrollBy,
  container = document.firstElementChild
} = {}) {
  const interval = useRef();
  const [type, setType] = useState(null);

  const handleDragEnd = () => {
    clearInterval(interval.current);
  };

  const handleDrag = event => {
    const { clientHeight } = container;
    const { clientY } = event;

    if (clientY >= clientHeight - 50) {
      setType("bottom");
    } else if (clientY > 0 && clientY <= 50) {
      setType("top");
    } else {
      setType(null);
    }
  };

  const cleanup = () => {
    container.removeEventListener("drag", handleDrag);
    container.removeEventListener("dragend", handleDragEnd);
  };

  const setup = () => {
    container.addEventListener("drag", handleDrag);
    container.addEventListener("dragend", handleDragEnd);
    return cleanup;
  };

  useEffect(setup, []);

  const getIntervalId = y =>
    setInterval(() => {
      scrollBy(0, y);
    }, 10);

  useEffect(() => {
    if (type === "top") {
      interval.current = getIntervalId(-1);
    } else if (type === "bottom") {
      interval.current = getIntervalId(1);
    } else {
      clearInterval(interval.current);
    }
  }, [type]);
  return [type];
}
