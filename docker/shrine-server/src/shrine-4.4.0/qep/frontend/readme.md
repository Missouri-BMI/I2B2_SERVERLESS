# Webclient 2.0

## To Get Started

This development environment requires [Node version 12.16.1.](https://nodejs.org/download/release/v12.16.1/) The easiest way to get up and running is to first install [Node Version Manager](https://github.com/creationix/nvm/blob/master/README.md) and use NVM to install Node 12.16.1.

<br/>

Install NVM:

```bash
curl -o- https://raw.githubusercontent.com/creationix/nvm/v0.34.0/install.sh | bash
```

<br/>

Use NVM to install Node 12.16.1:

```bash
nvm install 12.16.1
```

<br/>

Use NVM to set Node 12.16.1 as the default version for any terminal you open:

```bash
nvm alias default 12.16.1
```

<br/>

From this project folder you can install all dependencies using NPM:

```bash
npm ci
```

<br/>

## Available Scripts

Run the app in the development mode:<br>

```bash
npm start
```

Open [http://localhost:6443](http://localhost:6443) to view it in the browser.
The page will reload if you make edits.<br>

<br/>

Launch the test in watch mode:

```bash
npm test:watch
```

<br/>
<br/>

Launch the test runner for a one-time run:

```bash
npm test
```

<br/>
<br/>

Build the app for production to the `dist` folder:

```bash
npm run build`
```

<br/>
<br/>

Install all production dependencies and build the app for production to the `dist` folder:

```bash
npm run build-prod
```

<br/>
<br>

Another way to handle more recent node versions which break the npm install:
If npm install fails around node-gyp and your version of node is higher than 14, do the following to downgrade node, which should fix the problem:

```bash
npm install -g n
sudo n 14
node -v  # --> should output "v14.21.3"
```
Before re-running ```npm install``` or even ```mvn clean install```, you may want to clear the npm cache:
```bash
npm cache clean --force
```

