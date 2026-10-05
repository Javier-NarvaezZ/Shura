package io.github.javiernarvaezz.shura.core.stream

/*
 * Portions derived from bgutils-js v4.0.3 (https://github.com/LuanRT/BgUtils):
 *
 * MIT License
 *
 * Copyright (c) 2024 LuanRT
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

/**
 * The JavaScript that runs inside the [JsRuntime] to execute BotGuard and mint tokens. It performs no network
 * access: the challenge, the interpreter and the integrity token are fetched from Kotlin.
 *
 * Written for Shura from bgutils-js's implementation of the BotGuard protocol, so it is treated as derived from it
 * (MIT, notice above and in THIRD_PARTY_NOTICES.md). The same notice is embedded in [BOOTSTRAP], so it ships with
 * every copy of the script.
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
        /*
         * Portions derived from bgutils-js v4.0.3 (https://github.com/LuanRT/BgUtils).
         * MIT License. Copyright (c) 2024 LuanRT.
         * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
         * documentation files (the "Software"), to deal in the Software without restriction, including without
         * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
         * Software, and to permit persons to whom the Software is furnished to do so, subject to the following
         * conditions: The above copyright notice and this permission notice shall be included in all copies or
         * substantial portions of the Software.
         * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
         * TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
         * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
         * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
         * DEALINGS IN THE SOFTWARE.
         */
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
