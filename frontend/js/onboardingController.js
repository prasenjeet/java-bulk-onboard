/**
 * OnboardingController
 *
 * Drives the entire onboarding UI:
 *   • File drag-and-drop / browse
 *   • CSV upload → receive jobId
 *   • SSE subscription for live progress
 *   • Display KPI tiles, progress bar, failures table, job history
 */
angular.module('bulkOnboardApp')
  .controller('OnboardingController', [
    '$scope', '$timeout', 'OnboardingService',
    function($scope, $timeout, OnboardingService) {
      var vm = this;
      var activeEventSource = null;

      // ── State ────────────────────────────────────────────────────── //
      vm.selectedFile   = null;
      vm.uploading      = false;
      vm.uploadError    = null;
      vm.isDragging     = false;
      vm.activeJob      = null;
      vm.jobHistory     = [];
      vm.currentYear    = new Date().getFullYear();

      // ── Initialization ───────────────────────────────────────────── //
      vm.refreshHistory();

      // ── File input helpers ───────────────────────────────────────── //

      vm.triggerFileInput = function() {
        document.getElementById('csvFileInput').click();
      };

      vm.onFileSelected = function(event) {
        var file = event.target.files[0];
        if (file) vm._setFile(file);
      };

      vm.onDragOver = function($event) {
        $event.preventDefault();
        $event.stopPropagation();
        $scope.$apply(function() { vm.isDragging = true; });
      };

      vm.onDragLeave = function($event) {
        $event.preventDefault();
        $scope.$apply(function() { vm.isDragging = false; });
      };

      vm.onDrop = function($event) {
        $event.preventDefault();
        $event.stopPropagation();
        var file = $event.dataTransfer.files[0];
        $scope.$apply(function() {
          vm.isDragging = false;
          if (file) vm._setFile(file);
        });
      };

      vm._setFile = function(file) {
        vm.uploadError = null;
        if (!file.name.toLowerCase().endsWith('.csv')) {
          vm.uploadError = 'Invalid file type. Please select a .csv file.';
          return;
        }
        vm.selectedFile = file;
      };

      vm.clearFile = function($event) {
        $event.stopPropagation();
        vm.selectedFile = null;
        vm.uploadError  = null;
        document.getElementById('csvFileInput').value = '';
      };

      // ── Upload ───────────────────────────────────────────────────── //

      vm.uploadFile = function() {
        if (!vm.selectedFile || vm.uploading) return;

        vm.uploading    = true;
        vm.uploadError  = null;
        vm.activeJob    = null;

        // Close any previous SSE connection
        if (activeEventSource) {
          activeEventSource.close();
          activeEventSource = null;
        }

        OnboardingService.uploadCsv(vm.selectedFile)
          .then(function(response) {
            vm.uploading = false;
            vm.selectedFile = null;
            document.getElementById('csvFileInput').value = '';

            // Seed the active-job with minimum info while SSE warms up
            vm.activeJob = {
              jobId:              response.jobId,
              fileName:           vm.selectedFile ? vm.selectedFile.name : '',
              state:              'QUEUED',
              totalRecords:       response.totalRecords,
              processedCount:     0,
              successCount:       0,
              failureCount:       0,
              percentageComplete: 0,
              percentageRemaining:100,
              failures:           []
            };

            // Scroll to progress section
            $timeout(function() {
              var el = document.getElementById('progress-section');
              if (el) el.scrollIntoView({ behavior: 'smooth' });
            }, 100);

            // Subscribe to SSE
            activeEventSource = OnboardingService.streamJobStatus(
              response.jobId,
              function onProgress(data) {
                $scope.$apply(function() { vm.activeJob = data; });
              },
              function onComplete(data) {
                $scope.$apply(function() {
                  if (data) vm.activeJob = data;
                  vm.refreshHistory();
                });
              },
              function onError(err) {
                $scope.$apply(function() {
                  vm.uploadError = 'Lost connection to server. Refresh to check status.';
                });
              }
            );
          })
          .catch(function(err) {
            vm.uploading   = false;
            vm.uploadError = (err.data && err.data.error)
              ? err.data.error
              : 'Upload failed. Is the server running on port 8080?';
          });
      };

      // ── Template download ────────────────────────────────────────── //

      vm.downloadTemplate = function() {
        OnboardingService.downloadTemplate();
      };

      // ── Job history ──────────────────────────────────────────────── //

      vm.refreshHistory = function() {
        OnboardingService.listJobs()
          .then(function(jobs) {
            $scope.$apply(function() { vm.jobHistory = jobs; });
          })
          .catch(angular.noop);   // silently ignore if server not yet ready
      };

      vm.viewJob = function(jobId) {
        OnboardingService.getJobStatus(jobId)
          .then(function(job) {
            $scope.$apply(function() { vm.activeJob = job; });
            $timeout(function() {
              var el = document.getElementById('progress-section');
              if (el) el.scrollIntoView({ behavior: 'smooth' });
            }, 100);
          });
      };

      // ── Export failures CSV ──────────────────────────────────────── //

      vm.exportFailures = function() {
        if (!vm.activeJob || !vm.activeJob.failures) return;

        var lines = ['account_id,account_name,email,failure_reason'];
        vm.activeJob.failures.forEach(function(f) {
          lines.push([
            '"' + (f.accountId   || '') + '"',
            '"' + (f.accountName || '') + '"',
            '"' + (f.email       || '') + '"',
            '"' + (f.failureReason.replace(/"/g, '""')) + '"'
          ].join(','));
        });

        var blob = new Blob([lines.join('\n')], { type: 'text/csv' });
        var url  = URL.createObjectURL(blob);
        var a    = document.createElement('a');
        a.href     = url;
        a.download = 'failures_' + vm.activeJob.jobId.slice(0, 8) + '.csv';
        a.click();
        URL.revokeObjectURL(url);
      };

      // ── UI helpers ───────────────────────────────────────────────── //

      vm.formatFileSize = function(bytes) {
        if (!bytes) return '0 B';
        var k = 1024;
        var sizes = ['B', 'KB', 'MB', 'GB'];
        var i = Math.floor(Math.log(bytes) / Math.log(k));
        return (bytes / Math.pow(k, i)).toFixed(1) + ' ' + sizes[i];
      };

      vm.formatDate = function(iso) {
        if (!iso) return '';
        return new Date(iso).toLocaleString();
      };

      vm.successRate = function(job) {
        if (!job || job.processedCount === 0) return 0;
        return (job.successCount / job.processedCount) * 100;
      };

      vm.stateBadgeClass = function(state) {
        return {
          'badge-queued':     state === 'QUEUED',
          'badge-processing': state === 'PROCESSING',
          'badge-completed':  state === 'COMPLETED',
          'badge-failed':     state === 'FAILED'
        };
      };

      vm.progressBarClass = function(state) {
        return {
          'progress-fill-processing': state === 'PROCESSING',
          'progress-fill-completed':  state === 'COMPLETED',
          'progress-fill-failed':     state === 'FAILED'
        };
      };

      // ── Cleanup ──────────────────────────────────────────────────── //
      $scope.$on('$destroy', function() {
        if (activeEventSource) activeEventSource.close();
      });
    }
  ]);
