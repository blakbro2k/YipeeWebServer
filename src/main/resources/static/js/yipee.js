//Bootstrap custom modal auto focus
$('#myModal').on('shown.bs.modal', function () {
  $('#myInput').trigger('focus')
})