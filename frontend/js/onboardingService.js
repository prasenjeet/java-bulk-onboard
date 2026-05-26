/**
 * OnboardingService – wraps all HTTP calls to the Spring Boot API.
 *
 * API base URL is configurable via the APP_CONFIG constant so it can be
 * overridden per environment without editing this file.
 */
angular.module('bulkOnboardApp')

  .constant('APP_CONFIG', {
    apiBase: 'http://localhost:8080/api/onboard'
  })

  .service('OnboardingService', ['$http', '$q', 'APP_CONFIG',
    function($http, $q, APP_CONFIG) {
      var self = this;
      var base = APP_CONFIG.apiBase;

      // ---------------------------------------------------------------- //
      // Upload a CSV file and return a promise that resolves to           //
      // { jobId, message, totalRecords }                                  //
      // ---------------------------------------------------------------- //
      self.uploadCsv = function(file) {
        var fd = new FormData();
        fd.append('file', file);

        return $http.post(base + '/upload', fd, {
          headers: { 'Content-Type': undefined },   // let browser set boundary
          transformRequest: angular.identity
        }).then(function(resp) {
          return resp.data;
        });
      };

      // ---------------------------------------------------------------- //
      // Poll job status once (JSON snapshot)                              //
      // ---------------------------------------------------------------- //
      self.getJobStatus = function(jobId) {
        return $http.get(base + '/jobs/' + jobId)
          .then(function(resp) { return resp.data; });
      };

      // ---------------------------------------------------------------- //
      // Subscribe to Server-Sent Events for a job.                       //
      // Returns the EventSource so the caller can close it.              //
      //                                                                   //
      // onProgress(data) – called on each 'progress' event               //
      // onComplete()     – called when the stream closes normally         //
      // onError(err)     – called on EventSource error                    //
      // ---------------------------------------------------------------- //
      self.streamJobStatus = function(jobId, onProgress, onComplete, onError) {
        if (!window.EventSource) {
          // Fallback: poll every second if SSE is not supported
          var cancelled = false;
          (function poll() {
            if (cancelled) return;
            self.getJobStatus(jobId).then(function(data) {
              onProgress(data);
              if (data.state === 'COMPLETED' || data.state === 'FAILED') {
                onComplete && onComplete(data);
              } else {
                setTimeout(poll, 1000);
              }
            }, onError);
          })();
          return { close: function() { cancelled = true; } };
        }

        var es = new EventSource(base + '/jobs/' + jobId + '/sse');

        es.addEventListener('progress', function(event) {
          try {
            var data = JSON.parse(event.data);
            onProgress(data);
            if (data.state === 'COMPLETED' || data.state === 'FAILED') {
              es.close();
              onComplete && onComplete(data);
            }
          } catch (e) {
            console.error('SSE parse error', e);
          }
        });

        es.addEventListener('error', function(event) {
          if (es.readyState === EventSource.CLOSED) {
            onComplete && onComplete();
          } else {
            onError && onError(event);
          }
        });

        return es;
      };

      // ---------------------------------------------------------------- //
      // List all jobs                                                     //
      // ---------------------------------------------------------------- //
      self.listJobs = function() {
        return $http.get(base + '/jobs')
          .then(function(resp) { return resp.data; });
      };

      // ---------------------------------------------------------------- //
      // Download blank template                                           //
      // ---------------------------------------------------------------- //
      self.downloadTemplate = function() {
        window.open(base + '/template', '_blank');
      };
    }
  ]);
