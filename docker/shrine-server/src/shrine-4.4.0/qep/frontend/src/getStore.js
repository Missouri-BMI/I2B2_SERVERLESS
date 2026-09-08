import { createStore, applyMiddleware, combineReducers } from 'redux';
import createSagaMiddleware from 'redux-saga';

import { initSagas } from './initSagas';
import * as appReducer from './reducers';
import { defaultState } from './defaultState';

let store;
export const getStore = () => {
  if (!store) {
    const sagaMiddleware = createSagaMiddleware();
    store = createStore(
      combineReducers(appReducer),
      defaultState,
      applyMiddleware(sagaMiddleware),
    );
    initSagas(sagaMiddleware);
  }
  return store;
};
