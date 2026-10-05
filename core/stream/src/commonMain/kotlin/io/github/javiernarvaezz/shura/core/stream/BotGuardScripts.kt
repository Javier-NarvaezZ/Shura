package io.github.javiernarvaezz.shura.core.stream

/**
 * The JavaScript that runs inside the [JsRuntime] to execute BotGuard and mint tokens. It performs no network
 * access: the challenge, the interpreter and the integrity token are fetched from Kotlin.
 *
 * Written for Shura. It follows the BotGuard protocol as implemented by bgutils-js v4.0.3
 * (https://github.com/LuanRT/BgUtils, MIT License, Copyright (c) LuanRT); no code was copied from it.
 *
 * Every function receives a request id first and reports exactly once through the bridge object
 * [BRIDGE]`.onResult(id, ok, payload)`. Failures report a short fixed code, never data.
 */
internal object BotGuardScripts {
    const val BRIDGE = "ShuraPoToken"
    const val RUN_BOTGUARD = "shuraRunBotGuard"
    const val CREATE_MINTER = "shuraCreateMinter"
    const val MINT = "shuraMint"

    val BOOTSTRAP: String =
        """
        (function () {
          'use strict';
          var bridge = globalThis.$BRIDGE;
          var signalOutput = null;
          var mintCallback = null;
          var noop = function () {};

          function done(id, value) { bridge.onResult(id, true, String(value)); }
          function fail(id, code) { bridge.onResult(id, false, code); }

          globalThis.$RUN_BOTGUARD = function (id, challenge, eventId) {
            try {
              globalThis.yt = { config_: { EVENT_ID: eventId } };
              // Indirect eval runs the interpreter in the global scope, where it defines globalThis[globalName].
              (0, eval)(challenge.interpreterJavascript.privateDoNotAccessOrElseSafeScriptWrappedValue);
              var vm = globalThis[challenge.globalName];
              if (!vm || typeof vm.a !== 'function') { fail(id, 'vm-unavailable'); return; }
              var output = [];
              var onReady = function (asyncSnapshot) {
                try {
                  asyncSnapshot(function (response) {
                    signalOutput = output;
                    done(id, response);
                  }, [undefined, undefined, output, undefined]);
                } catch (e) { fail(id, 'snapshot'); }
              };
              vm.a(challenge.program, onReady, true, undefined, noop, [[], []], undefined, false,
                [noop, noop, noop, noop, noop]);
            } catch (e) { fail(id, 'load'); }
          };

          globalThis.$CREATE_MINTER = function (id, integrityToken) {
            if (!signalOutput || typeof signalOutput[0] !== 'function') { fail(id, 'no-signal'); return; }
            Promise.resolve().then(function () { return signalOutput[0](integrityToken); }).then(function (callback) {
              if (typeof callback !== 'function') { fail(id, 'no-minter'); return; }
              mintCallback = callback;
              done(id, 'ready');
            }, function () { fail(id, 'minter'); });
          };

          globalThis.$MINT = function (id, identifier) {
            if (!mintCallback) { fail(id, 'no-minter'); return; }
            Promise.resolve().then(function () { return mintCallback(identifier); }).then(function (bytes) {
              if (!(bytes instanceof Uint8Array)) { fail(id, 'mint-invalid'); return; }
              done(id, Array.prototype.join.call(bytes, ','));
            }, function () { fail(id, 'mint'); });
          };
        })();
        """.trimIndent()
}
