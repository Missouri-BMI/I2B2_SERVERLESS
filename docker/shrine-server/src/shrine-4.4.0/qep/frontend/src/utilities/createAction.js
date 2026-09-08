const filterKeys = (obj, retainedKeys) =>
  Object.keys(obj).filter(k => retainedKeys.includes(k)).reduce((acc, key) => ({ ...acc, [key]: obj[key] }), {});

export const createAction = type => (payload = {}) => ({ type, payload });

export const createNamedArgsAction = (type, ...argNames) => (providedPayload) =>
  ({ type, payload: filterKeys(providedPayload || {}, argNames || []) });
