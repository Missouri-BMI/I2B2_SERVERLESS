import { useEffect, useState } from "react";

export function useInfiniteScroll({ onScrollReachedBottom }) {
  const [scrollRef, setScrollRef] = useState(null);

  const handleScroll = () => {
    const { scrollHeight, offsetHeight, scrollTop } = scrollRef;
    const totalScrollDistance = scrollHeight - offsetHeight;
    const scrollReachedBottom =
      Math.ceil(scrollTop) >= Math.floor(totalScrollDistance);

    if (scrollReachedBottom) {
      onScrollReachedBottom();
    }
  };

  const cleanupContainerBeforeRemovedFromDOM = () => {
    if (scrollRef) {
      scrollRef.removeEventListener("scroll", handleScroll, false);
      setScrollRef(null);
    }
  };

  const setupContainerAfterItsAddedToDOM = () => {
    if (scrollRef) {
      scrollRef.addEventListener("scroll", handleScroll, false);
      return cleanupContainerBeforeRemovedFromDOM;
    }
  };

  const setScrollReference = (element) => {
    if (element) {
      setScrollRef(element);
    }
  };

  useEffect(setupContainerAfterItsAddedToDOM, [scrollRef]);

  return { setScrollReference };
}
