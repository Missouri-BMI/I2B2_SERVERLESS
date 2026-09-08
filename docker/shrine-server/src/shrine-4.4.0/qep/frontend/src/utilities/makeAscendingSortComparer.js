export const makeAscendingSortComparer = selector => (a, b) => {
  if (selector(a) < selector(b)) return -1;
  else if (selector(a) > selector(b)) return 1;
  return 0;
};
