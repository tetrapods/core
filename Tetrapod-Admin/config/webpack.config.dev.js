var autoprefixer = require('autoprefixer');
var webpack = require('webpack');
var HtmlWebpackPlugin = require('html-webpack-plugin');
var CaseSensitivePathsPlugin = require('case-sensitive-paths-webpack-plugin');
var InterpolateHtmlPlugin = require('react-dev-utils/InterpolateHtmlPlugin');
var WatchMissingNodeModulesPlugin = require('react-dev-utils/WatchMissingNodeModulesPlugin');
var getClientEnvironment = require('./env');
var paths = require('./paths');


// Webpack uses `publicPath` to determine where the app is being served from.
// In development, we always serve from the root. This makes config easier.
var publicPath = '/';
// Get environment variables to inject into our app.
var env = getClientEnvironment();

// This is the development configuration.
// It is focused on developer experience and fast rebuilds.
// The production configuration is different and lives in a separate file.
module.exports = {
   // You may want 'eval' instead if you prefer to see the compiled output in DevTools.
   // See the discussion in https://github.com/facebookincubator/create-react-app/issues/343.
   devtool: 'source-map',
   // These are the "entry points" to our application.
   // This means they will be the "root" imports that are included in JS bundle.
   // The first two entry points enable "hot" CSS and auto-refreshes for JS.
   entry: [
      // Include an alternative client for WebpackDevServer. A client's job is to
      // connect to WebpackDevServer by a socket and get notified about changes.
      // When you save a file, the client will either apply hot updates (in case
      // of CSS changes), or refresh the page (in case of JS changes). When you
      // make a syntax error, this client will display a syntax error overlay.
      // Note: instead of the default WebpackDevServer client, we use a custom one
      // to bring better experience for Create React App users. You can replace
      // the line below with these two lines if you prefer the stock client:
      // require.resolve('webpack-dev-server/client') + '?/',
      // require.resolve('webpack/hot/dev-server'),
      require.resolve('react-dev-utils/webpackHotDevClient'),
      // We ship a few polyfills by default:
      require.resolve('./polyfills'),
      // Finally, this is your app's code:
      paths.appIndexJs
      // We include the app code last so that if there is a runtime error during
      // initialization, it doesn't blow up the WebpackDevServer client, and
      // changing JS code would still trigger a refresh.
   ],
   output: {
      // Next line is not used in dev but WebpackDevServer crashes without it:
      path: paths.appBuild,
      // Add /* filename */ comments to generated require()s in the output.
      pathinfo: true,
      // This does not produce a real file. It's just the virtual path that is
      // served by WebpackDevServer in development. This is the JS bundle
      // containing code from all our entry points, and the Webpack runtime.
      filename: 'static/js/bundle.js',
      // This is the URL that app is served from. We use "/" in development.
      publicPath: publicPath
   },
   resolve: {
      // This allows you to set a fallback for where Webpack should look for modules.
      // We read `NODE_PATH` environment variable in `paths.js` and pass paths here.
      // We use `fallback` instead of `root` because we want `node_modules` to "win"
      // if there any conflicts. This matches Node resolution mechanism.
      // https://github.com/facebookincubator/create-react-app/issues/253
      fallback: paths.nodePaths.concat([paths.appSrc]),
      // These are the reasonable defaults supported by the Node ecosystem.
      // We also include JSX as a common component filename extension to support
      // some tools, although we do not recommend using it, see:
      // https://github.com/facebookincubator/create-react-app/issues/290
      extensions: ['.ts', '.tsx', '.js', '.json', '.jsx', ''],
      alias: {
         // Support React Native Web
         // https://www.smashingmagazine.com/2016/08/a-glimpse-into-the-future-with-react-native-for-web/
         'react-native': 'react-native-web'
      }
   },
   module: {
      preLoaders: [
         {test: /\.(ts|tsx)$/, loader: 'tslint', include: paths.appSrc}
      ],
      loaders: [
         // Default loader: load all assets that are not handled
         // by other loaders with the url loader.
         // Note: This list needs to be updated with every change of extensions
         // the other loaders match.
         // E.g., when adding a loader for a new supported file extension,
         // we need to add the supported extension to this loader too.
         // Add one new line in `exclude` for each loader.
         //
         // "file" loader makes sure those assets get served by WebpackDevServer.
         // When you `import` an asset, you get its (virtual) filename.
         // In production, they would get copied to the `build` folder.
         // "url" loader works like "file" loader except that it embeds assets
         // smaller than specified limit in bytes as data URLs to avoid requests.
         // A missing `test` is equivalent to a match.
         {
            exclude: [
               /\.html$/,
               /\.(js|jsx)$/,
               /\.(ts|tsx)$/,
               /\.scss$/,
               /\.css$/,
               /\.json$/
            ],
            loader: 'url',
            query: {
               limit: 10000,
               name: 'static/media/[name].[hash:8].[ext]'
            }
         },
         {test: /\.(ts|tsx)$/, include: paths.appSrc, loader: 'ts'},
         {test: /\.scss$/, loader: 'style!css?importLoaders=1!postcss!sass'},
         {test: /\.css$/, loader: 'style!css?importLoaders=1!postcss'},
         {test: /\.json$/, loader: 'json'},
      ]
   },
   // We use PostCSS for autoprefixing only.
   postcss: function () {
      return [
         autoprefixer({
            browsers: [
               '>1%',
               'last 4 versions',
               'Firefox ESR',
               'not ie < 9', // React doesn't support IE8 anyway
            ]
         }),
      ];
   },
   plugins: [
       // Generates an `index.html` file with the <script> injected.
      new HtmlWebpackPlugin({
         inject: true,
         template: paths.appHtml,
      }),
      // Makes some environment variables available to the JS code, for example:
      // if (process.env.NODE_ENV === 'development') { ... }. See `./env.js`.
      new webpack.DefinePlugin(env),
      // This is necessary to emit hot updates (currently CSS only):
      new webpack.HotModuleReplacementPlugin(),
      // Watcher doesn't work well if you mistype casing in a path so we use
      // a plugin that prints an error when you attempt to do this.
      // See https://github.com/facebookincubator/create-react-app/issues/240
      new CaseSensitivePathsPlugin(),
      // If you require a missing module and then `npm install` it, you still have
      // to restart the development server for Webpack to discover it. This plugin
      // makes the discovery automatic so you don't have to restart.
      // See https://github.com/facebookincubator/create-react-app/issues/186
      new WatchMissingNodeModulesPlugin(paths.appNodeModules)
   ],
   // Some libraries import Node modules but don't use them in the browser.
   // Tell Webpack to provide empty mocks for them so importing them works.
   node: {
      fs: 'empty',
      net: 'empty',
      tls: 'empty'
   }
};