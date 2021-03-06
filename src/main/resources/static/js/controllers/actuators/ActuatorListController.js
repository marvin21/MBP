/* global app */

/**
 * Controller for the actuator list page.
 */
app.controller('ActuatorListController',
    ['$scope', '$controller', '$interval', 'actuatorList', 'addActuator', 'deleteActuator',
      'deviceList', 'adapterList', 'ComponentService', 'ComponentTypeService', 'NotificationService',
      function ($scope, $controller, $interval, actuatorList, addActuator, deleteActuator,
                deviceList, adapterList, ComponentService, ComponentTypeService, NotificationService) {
        var vm = this;

        vm.adapterList = adapterList;
        vm.deviceList = deviceList;

        /**
         * Initializing function, sets up basic things.
         */
        (function initController() {
          loadActuatorTypes();
          loadActuatorStates();

          //Interval for updating actuator states on a regular basis
          var interval = $interval(function () {
            loadActuatorStates();
          }, 5 * 60 * 1000);

          //Cancel interval on route change
          $scope.$on('$destroy', function () {
            $interval.cancel(interval);
          });
        })();

        //Extend each actuator in actuatorList for a state and a reload function
        for (var i in actuatorList) {
          actuatorList[i].state = 'LOADING';
          actuatorList[i].reloadState = createReloadStateFunction(actuatorList[i].id);
        }

        /**
         * [Public]
         * @param actuator
         * @returns {*}
         */
        $scope.detailsLink = function (actuator) {
          if (actuator.id) {
            return "view/actuators/" + actuator.id;
          }
          return "#";
        };

        /**
         * [Public]
         * Shows an alert that asks the user if he is sure that he wants to delete a certain actuator.
         *
         * @param data A data object that contains the id of the actuator that is supposed to be deleted
         * @returns A promise of the user's decision
         */
        function confirmDelete(data) {
          var actuatorId = data.id;
          var actuatorName = "";

          //Determines the actuator's name by checking all actuators in the actuator list
          for (var i = 0; i < actuatorList.length; i++) {
            if (actuatorId == actuatorList[i].id) {
              actuatorName = actuatorList[i].name;
              break;
            }
          }

          //Show the alert to the user and return the resulting promise
          return Swal.fire({
            title: 'Delete actuator',
            type: 'warning',
            html: "Are you sure you want to delete actuator \"" + actuatorName + "\"?",
            showCancelButton: true,
            confirmButtonText: 'Delete',
            confirmButtonClass: 'bg-red',
            focusConfirm: false,
            cancelButtonText: 'Cancel'
          });
        }

        /**
         * [Private]
         * Returns a function that retrieves the state for a actuator with a certain id.
         * @param id The id of the actuator
         * @returns {Function}
         */
        function createReloadStateFunction(id) {
          //Create function and return it
          return function () {
            getActuatorState(id);
          };
        }

        /**
         * [Private]
         * Sends a server request in order to retrieve the deployment state of a actuator with a certain id.
         * The state is then stored in the corresponding actuator object in actuatorList.
         *
         * @param id The id of the actuator whose state is supposed to be retrieved
         */
        function getActuatorState(id) {
          //Resolve actuator object of the affected actuator
          var actuator = null;
          for (var i = 0; i < actuatorList.length; i++) {
            if (actuatorList[i].id == id) {
              actuator = actuatorList[i];
            }
          }

          //Check if actuator could be found
          if (actuator == null) {
            return;
          }

          //Enable spinner
          actuator.state = 'LOADING';

          //Perform server request and set state of the actuator object accordingly
          ComponentService.getComponentState(actuator.id, 'actuators').then(function (response) {
            actuator.state = response.data.content;
          }, function (response) {
            actuator.state = 'UNKNOWN';
            NotificationService.notify("Could not retrieve the actuator state.", "error");
          });
        }

        /**
         * [Private]
         * Sends a server request in order to retrieve the deployment states of all registered actuators.
         * The states are then stored in the corresponding actuator objects in actuatorList.
         */
        function loadActuatorStates() {//Perform server request

          ComponentService.getAllComponentStates('actuators').then(function (response) {
            var statesMap = response.data;

            //Iterate over all actuators in actuatorList and update the states of all actuators accordingly
            for (var i in actuatorList) {
              var actuatorId = actuatorList[i].id;
              actuatorList[i].state = statesMap[actuatorId];
            }
          }, function (response) {
            for (var i in actuatorList) {
              actuatorList[i].state = 'UNKNOWN';
            }
            NotificationService.notify("Could not retrieve actuator states.", "error");
          });
        }

        //Expose
        angular.extend(vm, {
          registeringDevice: false
        });

        // expose controller ($controller will auto-add to $scope)
        angular.extend(vm, {
          actuatorListCtrl: $controller('ItemListController as actuatorListCtrl', {
            $scope: $scope,
            list: actuatorList
          }),
          addActuatorCtrl: $controller('AddItemController as addActuatorCtrl', {
            $scope: $scope,
            addItem: addActuator
          }),
          deleteActuatorCtrl: $controller('DeleteItemController as deleteActuatorCtrl', {
            $scope: $scope,
            deleteItem: deleteActuator,
            confirmDeletion: confirmDelete
          })
        });

        // $watch 'addActuator' result and add to 'actuatorList'
        $scope.$watch(
            function () {
              //Value being watched
              return vm.addActuatorCtrl.result;
            },
            function () {
              //Callback
              var actuator = vm.addActuatorCtrl.result;

              if (actuator) {
                //Close modal on success
                $("#addActuatorModal").modal('toggle');

                //Add state and reload function to the new object
                actuator.state = 'LOADING';
                actuator.reloadState = createReloadStateFunction(actuator.id);

                //Add actuator to actuator list
                vm.actuatorListCtrl.pushItem(actuator);

                //Retrieve state of the new actuator
                getActuatorState(actuator.id);
              }
            }
        );

        // $watch 'deleteItem' result and remove from 'itemList'
        $scope.$watch(
            function () {
              // value being watched
              return vm.deleteActuatorCtrl.result;
            },
            function () {
              var id = vm.deleteActuatorCtrl.result;

              vm.actuatorListCtrl.removeItem(id);
            }
        );

        function loadActuatorTypes() {
          ComponentTypeService.GetByComponent('ACTUATOR')
              .then(function (response) {
                if (response.success) {
                  vm.actuatorTypes = response.data;
                } else {
                  console.log("Error loading actuator types!");
                }
              });
        };

      }
    ]);