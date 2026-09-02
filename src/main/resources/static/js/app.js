// ===== YT Stream Spring Boot UI Scripts =====

document.addEventListener('DOMContentLoaded', () => {
    initSearch();
    initPlayer();
    initDescriptionToggle();
    initAudioStudio();
});

// ===== Search & Autocomplete =====
function initSearch() {
    const searchInput = document.getElementById('search-input');
    const searchClear = document.getElementById('search-clear');
    const suggestionsDropdown = document.getElementById('suggestions-dropdown');
    const searchForm = document.getElementById('search-form');

    if (!searchInput) return;

    let debounceTimer = null;
    let activeSuggestionIndex = -1;

    const updateClearBtn = () => {
        if (searchClear) {
            searchClear.classList.toggle('visible', searchInput.value.trim().length > 0);
        }
    };
    updateClearBtn();

    searchInput.addEventListener('input', (e) => {
        updateClearBtn();
        const query = e.target.value.trim();

        clearTimeout(debounceTimer);
        if (query.length > 1) {
            debounceTimer = setTimeout(() => fetchSuggestions(query), 200);
        } else {
            closeSuggestions();
        }
    });

    searchInput.addEventListener('keydown', (e) => {
        if (!suggestionsDropdown) return;
        const items = suggestionsDropdown.querySelectorAll('.suggestion-item');

        if (e.key === 'ArrowDown') {
            e.preventDefault();
            activeSuggestionIndex = Math.min(activeSuggestionIndex + 1, items.length - 1);
            updateActiveItem(items);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            activeSuggestionIndex = Math.max(activeSuggestionIndex - 1, -1);
            updateActiveItem(items);
        } else if (e.key === 'Enter') {
            if (activeSuggestionIndex >= 0 && items[activeSuggestionIndex]) {
                e.preventDefault();
                searchInput.value = items[activeSuggestionIndex].dataset.text;
                closeSuggestions();
                searchForm.submit();
            }
        } else if (e.key === 'Escape') {
            closeSuggestions();
        }
    });

    if (searchClear) {
        searchClear.addEventListener('click', () => {
            searchInput.value = '';
            updateClearBtn();
            closeSuggestions();
            searchInput.focus();
        });
    }

    document.addEventListener('click', (e) => {
        if (!e.target.closest('.search-container')) {
            closeSuggestions();
        }
    });

    function updateActiveItem(items) {
        items.forEach((item, idx) => {
            item.classList.toggle('active', idx === activeSuggestionIndex);
        });
    }

    async function fetchSuggestions(query) {
        try {
            const res = await fetch(`/api/suggestions?q=${encodeURIComponent(query)}`);
            const data = await res.json();
            const suggestions = data.suggestions || [];

            if (suggestions.length === 0) {
                closeSuggestions();
                return;
            }

            suggestionsDropdown.innerHTML = suggestions.slice(0, 8).map(text => `
                <div class="suggestion-item" data-text="${escapeHtml(text)}">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                    <span>${escapeHtml(text)}</span>
                </div>
            `).join('');

            suggestionsDropdown.querySelectorAll('.suggestion-item').forEach(item => {
                item.addEventListener('click', () => {
                    searchInput.value = item.dataset.text;
                    closeSuggestions();
                    searchForm.submit();
                });
            });

            activeSuggestionIndex = -1;
            suggestionsDropdown.classList.add('active');
        } catch (err) {
            console.debug('Suggestions error:', err);
        }
    }

    function closeSuggestions() {
        if (suggestionsDropdown) {
            suggestionsDropdown.classList.remove('active');
            activeSuggestionIndex = -1;
        }
    }
}

// ===== Video Player Lifecycle & Buffering =====
function initPlayer() {
    const video = document.getElementById('video-player');
    const loadingOverlay = document.getElementById('player-loading');
    if (!video || !loadingOverlay) return;

    const loadingText = loadingOverlay.querySelector('.loading-text');

    video.addEventListener('loadstart', () => {
        loadingOverlay.style.display = 'flex';
        if (loadingText) loadingText.textContent = 'Connecting to stream chunks...';
    });

    video.addEventListener('waiting', () => {
        loadingOverlay.style.display = 'flex';
        if (loadingText) loadingText.textContent = 'Buffering stream chunks...';
    });

    video.addEventListener('canplay', () => {
        loadingOverlay.style.display = 'none';
        video.play().catch(err => {
            console.log('Autoplay was prevented by browser, click play to start:', err);
        });
    });

    video.addEventListener('playing', () => {
        loadingOverlay.style.display = 'none';
    });

    video.addEventListener('error', () => {
        loadingOverlay.style.display = 'none';
        console.error('Video error:', video.error);
        if (loadingText) loadingText.textContent = 'Unable to play stream. Retrying...';
    });
}

// ===== Audio Studio: Equalizer & Visualizer =====
function initAudioStudio() {
    const video = document.getElementById('video-player');
    const canvas = document.getElementById('visualizer-canvas');
    if (!video || !canvas) return;

    // Web Audio state
    let audioCtx = null;
    let sourceNode = null;
    let analyserNode = null;
    let preampGain = null;
    let filters = [];
    let isBypassed = false;
    let visualizerActive = true;
    let visualizerMode = 'bars';   // 'bars', 'wave', 'radial'
    let visualizerTheme = 'neon';  // 'neon', 'sunset', 'matrix'
    let animationFrameId = null;

    // Filter frequencies
    const FREQUENCIES = [60, 150, 400, 1000, 2400, 6000, 12000, 16000];
    const currentGains = [0, 0, 0, 0, 0, 0, 0, 0];
    const peakCaps = []; // For spectrum bars falling caps

    // Equalizer Presets
    const PRESETS = {
        flat:       [0, 0, 0, 0, 0, 0, 0, 0],
        bass:       [8, 6, 2, 0, 0, 0, 0, 0],
        lofi:       [5, 4, 2, -1, -2, -3, -5, -7],
        vocal:      [-3, -1, 2, 5, 4, 2, 1, 0],
        electronic: [8, 6, -1, 2, 3, 5, 6, 5],
        rock:       [5, 3, -2, 1, 3, 5, 4, 3],
        acoustic:   [3, 2, 1, 2, 3, 4, 3, 2],
        treble:     [-2, 0, 0, 2, 4, 7, 8, 9]
    };

    // Lazy init audio context on first user interaction or video play
    function setupAudioContext() {
        if (audioCtx) {
            if (audioCtx.state === 'suspended') {
                audioCtx.resume();
            }
            return;
        }

        try {
            const AudioContextClass = window.AudioContext || window.webkitAudioContext;
            audioCtx = new AudioContextClass();

            sourceNode = audioCtx.createMediaElementSource(video);

            // Analyser Node
            analyserNode = audioCtx.createAnalyser();
            analyserNode.fftSize = 256;
            analyserNode.smoothingTimeConstant = 0.82;

            // Preamp Gain Node
            preampGain = audioCtx.createGain();
            preampGain.gain.value = 1.0;

            // 8-Band Biquad Filters
            filters = FREQUENCIES.map((freq, i) => {
                const filter = audioCtx.createBiquadFilter();
                if (i === 0) {
                    filter.type = 'lowshelf';
                } else if (i === FREQUENCIES.length - 1) {
                    filter.type = 'highshelf';
                } else {
                    filter.type = 'peaking';
                    filter.Q.value = 1.2;
                }
                filter.frequency.value = freq;
                filter.gain.value = currentGains[i];
                return filter;
            });

            // Connect Audio Graph:
            // source -> preamp -> filter[0] -> ... -> filter[7] -> analyser -> destination
            let lastNode = sourceNode;
            lastNode.connect(preampGain);
            lastNode = preampGain;

            for (const filter of filters) {
                lastNode.connect(filter);
                lastNode = filter;
            }

            lastNode.connect(analyserNode);
            analyserNode.connect(audioCtx.destination);

            startVisualizerLoop();
        } catch (e) {
            console.warn('Web Audio API setup notice:', e);
        }
    }

    video.addEventListener('play', setupAudioContext);
    video.addEventListener('click', setupAudioContext);
    document.addEventListener('click', () => {
        if (audioCtx && audioCtx.state === 'suspended') {
            audioCtx.resume();
        }
    }, { once: true });

    // ===== Visualizer Canvas Render Loop =====
    const ctx = canvas.getContext('2d');

    function resizeCanvas() {
        const dpr = window.devicePixelRatio || 1;
        const rect = canvas.getBoundingClientRect();
        if (rect.width === 0 || rect.height === 0) return;
        canvas.width = rect.width * dpr;
        canvas.height = rect.height * dpr;
        ctx.scale(dpr, dpr);
    }
    window.addEventListener('resize', resizeCanvas);
    resizeCanvas();

    function startVisualizerLoop() {
        if (animationFrameId) return;

        const bufferLength = analyserNode.frequencyBinCount;
        const freqData = new Uint8Array(bufferLength);
        const timeData = new Uint8Array(bufferLength);

        function draw() {
            animationFrameId = requestAnimationFrame(draw);

            if (!visualizerActive) return;

            const width = canvas.getBoundingClientRect().width;
            const height = canvas.getBoundingClientRect().height;

            if (width === 0 || height === 0) return;

            ctx.clearRect(0, 0, width, height);

            if (visualizerMode === 'wave') {
                analyserNode.getByteTimeDomainData(timeData);
                drawWaveform(timeData, width, height);
            } else if (visualizerMode === 'radial') {
                analyserNode.getByteFrequencyData(freqData);
                drawRadial(freqData, width, height);
            } else {
                analyserNode.getByteFrequencyData(freqData);
                drawBars(freqData, width, height);
            }
        }

        draw();
    }

    // Cyberpunk 2077 color palettes generator
    function getThemeGradients(ctx, height) {
        let grad = ctx.createLinearGradient(0, height, 0, 0);
        let glowColor = 'rgba(0, 240, 255, 0.7)';

        if (visualizerTheme === 'sunset') {
            grad.addColorStop(0, '#ff003c');
            grad.addColorStop(0.5, '#ff7700');
            grad.addColorStop(1, '#fcee0a');
            glowColor = 'rgba(252, 238, 10, 0.8)';
        } else if (visualizerTheme === 'matrix') {
            grad.addColorStop(0, '#00f0ff');
            grad.addColorStop(0.5, '#00ff66');
            grad.addColorStop(1, '#a6ff00');
            glowColor = 'rgba(0, 255, 102, 0.8)';
        } else {
            // Night City Neon default
            grad.addColorStop(0, '#fcee0a');
            grad.addColorStop(0.45, '#00f0ff');
            grad.addColorStop(1, '#ff0055');
            glowColor = 'rgba(0, 240, 255, 0.8)';
        }

        return { grad, glowColor };
    }

    // Mode 1: Spectrum Bars with Top Falling Caps
    function drawBars(data, width, height) {
        const barCount = 48;
        const totalSpacing = width * 0.15;
        const barWidth = (width - totalSpacing) / barCount;
        const spacing = totalSpacing / (barCount - 1);
        const { grad, glowColor } = getThemeGradients(ctx, height);

        for (let i = 0; i < barCount; i++) {
            // Pick logarithmic/weighted index from FFT bins
            const dataIndex = Math.floor(Math.pow(i / barCount, 1.4) * (data.length * 0.75));
            const value = data[dataIndex] || 0;
            const percent = value / 255;
            const barHeight = Math.max(4, percent * (height - 18));
            const x = i * (barWidth + spacing);
            const y = height - barHeight;

            // Bar fill
            ctx.fillStyle = grad;
            ctx.shadowBlur = 8;
            ctx.shadowColor = glowColor;

            // Rounded bar top
            const r = Math.min(barWidth / 2, 4);
            ctx.beginPath();
            ctx.moveTo(x, height);
            ctx.lineTo(x, y + r);
            ctx.quadraticCurveTo(x, y, x + r, y);
            ctx.lineTo(x + barWidth - r, y);
            ctx.quadraticCurveTo(x + barWidth, y, x + barWidth, y + r);
            ctx.lineTo(x + barWidth, height);
            ctx.closePath();
            ctx.fill();

            // Falling peak cap
            if (!peakCaps[i]) peakCaps[i] = 0;
            if (y < peakCaps[i] || peakCaps[i] === 0) {
                peakCaps[i] = y;
            } else {
                peakCaps[i] += 1.2; // Gravity drop
            }

            const capY = Math.min(height - 4, peakCaps[i]);
            ctx.fillStyle = '#ffffff';
            ctx.shadowBlur = 12;
            ctx.shadowColor = '#ffffff';
            ctx.fillRect(x, capY, barWidth, 2.5);
        }
        ctx.shadowBlur = 0;
    }

    // Mode 2: Oscilloscope Waveform
    function drawWaveform(data, width, height) {
        const { glowColor } = getThemeGradients(ctx, height);
        const sliceWidth = width / data.length;

        ctx.lineWidth = 2.5;
        ctx.strokeStyle = visualizerTheme === 'sunset' ? '#f43f5e' : (visualizerTheme === 'matrix' ? '#10b981' : '#00f5d4');
        ctx.shadowBlur = 14;
        ctx.shadowColor = glowColor;

        ctx.beginPath();
        let x = 0;
        for (let i = 0; i < data.length; i++) {
            const v = data[i] / 128.0;
            const y = (v * height) / 2;
            if (i === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
            x += sliceWidth;
        }
        ctx.lineTo(width, height / 2);
        ctx.stroke();
        ctx.shadowBlur = 0;
    }

    // Mode 3: Radial Pulse
    function drawRadial(data, width, height) {
        const centerX = width / 2;
        const centerY = height / 2;
        const radius = Math.min(centerX, centerY) * 0.45;
        const barCount = 64;
        const { grad, glowColor } = getThemeGradients(ctx, height);

        ctx.shadowBlur = 10;
        ctx.shadowColor = glowColor;
        ctx.strokeStyle = grad;
        ctx.lineWidth = 3;

        for (let i = 0; i < barCount; i++) {
            const rad = (i * 2 * Math.PI) / barCount;
            const val = data[i % (data.length / 2)] || 0;
            const barLen = (val / 255) * (radius * 0.9);

            const x1 = centerX + Math.cos(rad) * radius;
            const y1 = centerY + Math.sin(rad) * radius;
            const x2 = centerX + Math.cos(rad) * (radius + barLen);
            const y2 = centerY + Math.sin(rad) * (radius + barLen);

            ctx.beginPath();
            ctx.moveTo(x1, y1);
            ctx.lineTo(x2, y2);
            ctx.stroke();
        }

        // Inner glowing core
        ctx.beginPath();
        ctx.arc(centerX, centerY, radius * 0.85, 0, 2 * Math.PI);
        ctx.strokeStyle = glowColor;
        ctx.lineWidth = 1.5;
        ctx.stroke();
        ctx.shadowBlur = 0;
    }

    // ===== Equalizer UI Listeners =====
    const eqPanel = document.getElementById('equalizer-panel');
    const toggleEqBtn = document.getElementById('toggle-equalizer-btn');
    const eqStatusBadge = document.getElementById('eq-status-badge');
    const bypassCheckbox = document.getElementById('eq-bypass-checkbox');
    const presetSelect = document.getElementById('eq-presets');
    const resetBtn = document.getElementById('eq-reset-btn');
    const preampSlider = document.getElementById('eq-preamp-slider');
    const preampVal = document.getElementById('preamp-val');

    // Toggle Equalizer Panel
    if (toggleEqBtn && eqPanel) {
        toggleEqBtn.addEventListener('click', () => {
            setupAudioContext();
            const isActive = eqPanel.classList.toggle('active');
            toggleEqBtn.classList.toggle('active', isActive);
        });
    }

    // Toggle Visualizer Display
    const toggleVisBtn = document.getElementById('toggle-visualizer-btn');
    const visContainer = document.getElementById('visualizer-container');
    if (toggleVisBtn && visContainer) {
        toggleVisBtn.addEventListener('click', () => {
            setupAudioContext();
            visualizerActive = !visualizerActive;
            visContainer.classList.toggle('hidden', !visualizerActive);
            toggleVisBtn.classList.toggle('active', visualizerActive);
            if (visualizerActive) resizeCanvas();
        });
    }

    // Visualizer Mode Buttons
    document.querySelectorAll('.mode-pill').forEach(pill => {
        pill.addEventListener('click', (e) => {
            setupAudioContext();
            document.querySelectorAll('.mode-pill').forEach(p => p.classList.remove('active'));
            e.currentTarget.classList.add('active');
            visualizerMode = e.currentTarget.dataset.mode;
        });
    });

    // Visualizer Theme Dots
    document.querySelectorAll('.theme-dot').forEach(dot => {
        dot.addEventListener('click', (e) => {
            document.querySelectorAll('.theme-dot').forEach(d => d.classList.remove('active'));
            e.currentTarget.classList.add('active');
            visualizerTheme = e.currentTarget.dataset.theme;
        });
    });

    // Update Band Gain
    function updateBandGain(index, gainValue) {
        currentGains[index] = parseFloat(gainValue);
        const gainLabel = document.getElementById(`gain-val-${index}`);
        if (gainLabel) {
            gainLabel.textContent = (currentGains[index] > 0 ? '+' : '') + currentGains[index] + ' dB';
        }

        if (!isBypassed && filters[index]) {
            filters[index].gain.setTargetAtTime(currentGains[index], audioCtx.currentTime, 0.02);
        }
    }

    // Sliders event listener
    for (let i = 0; i < FREQUENCIES.length; i++) {
        const slider = document.getElementById(`eq-slider-${i}`);
        if (slider) {
            slider.addEventListener('input', (e) => {
                setupAudioContext();
                updateBandGain(i, e.target.value);
                if (presetSelect) presetSelect.value = 'custom';
            });
        }
    }

    // Preamp Slider
    if (preampSlider && preampVal) {
        preampSlider.addEventListener('input', (e) => {
            setupAudioContext();
            const val = parseFloat(e.target.value);
            preampVal.textContent = val.toFixed(2) + 'x';
            if (preampGain && audioCtx) {
                preampGain.gain.setTargetAtTime(val, audioCtx.currentTime, 0.02);
            }
        });
    }

    // Preset Selection
    if (presetSelect) {
        presetSelect.addEventListener('change', (e) => {
            setupAudioContext();
            const preset = PRESETS[e.target.value];
            if (preset) {
                preset.forEach((gain, i) => {
                    const slider = document.getElementById(`eq-slider-${i}`);
                    if (slider) slider.value = gain;
                    updateBandGain(i, gain);
                });
            }
        });
    }

    // Reset EQ
    if (resetBtn) {
        resetBtn.addEventListener('click', () => {
            setupAudioContext();
            if (presetSelect) presetSelect.value = 'flat';
            PRESETS.flat.forEach((gain, i) => {
                const slider = document.getElementById(`eq-slider-${i}`);
                if (slider) slider.value = gain;
                updateBandGain(i, gain);
            });
            if (preampSlider && preampVal) {
                preampSlider.value = 1.0;
                preampVal.textContent = '1.0x';
                if (preampGain && audioCtx) {
                    preampGain.gain.setTargetAtTime(1.0, audioCtx.currentTime, 0.02);
                }
            }
        });
    }

    // Bypass / Enable Toggle
    if (bypassCheckbox) {
        bypassCheckbox.addEventListener('change', (e) => {
            setupAudioContext();
            isBypassed = !e.target.checked;
            if (eqStatusBadge) {
                eqStatusBadge.textContent = isBypassed ? 'BYPASS' : 'ACTIVE';
                eqStatusBadge.classList.toggle('disabled', isBypassed);
            }

            if (filters.length && audioCtx) {
                filters.forEach((filter, i) => {
                    const target = isBypassed ? 0 : currentGains[i];
                    filter.gain.setTargetAtTime(target, audioCtx.currentTime, 0.02);
                });
            }
        });
    }
}

// ===== Description Box Toggle =====
function initDescriptionToggle() {
    const descText = document.getElementById('player-description');
    const descToggle = document.getElementById('desc-toggle');

    if (descText && descToggle) {
        descToggle.addEventListener('click', () => {
            const isExpanded = descText.classList.toggle('expanded');
            descToggle.textContent = isExpanded ? 'Show less' : 'Show more';
        });
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
