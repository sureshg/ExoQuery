config.set({
    browsers: ['ChromeHeadless'],
    client: {
        captureConsole: true,
        mocha: {
            timeout: 10000
        }
    },
    reporters: ['mocha'],
    browserConsoleLogOptions: {
        level: 'debug',
        format: '%b %T: %m',
        terminal: true
    }
});
